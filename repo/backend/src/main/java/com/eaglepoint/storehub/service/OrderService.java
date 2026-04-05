package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.annotation.Audited;
import com.eaglepoint.storehub.annotation.DataScope;
import com.eaglepoint.storehub.aspect.DataScopeContext;
import com.eaglepoint.storehub.dto.OrderRequest;
import com.eaglepoint.storehub.dto.OrderResponse;
import com.eaglepoint.storehub.entity.DeliveryZone;
import com.eaglepoint.storehub.entity.Order;
import com.eaglepoint.storehub.entity.Organization;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.enums.FulfillmentMode;
import com.eaglepoint.storehub.enums.OrderStatus;
import com.eaglepoint.storehub.entity.PickupRedemptionLog;
import com.eaglepoint.storehub.repository.DeliveryZoneRepository;
import com.eaglepoint.storehub.repository.OrderRepository;
import com.eaglepoint.storehub.repository.OrganizationRepository;
import com.eaglepoint.storehub.repository.PickupRedemptionLogRepository;
import com.eaglepoint.storehub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final BigDecimal SHORT_RANGE_FEE = new BigDecimal("4.99");   // 0-5 miles
    private static final BigDecimal LONG_RANGE_FEE = new BigDecimal("7.99");    // 5-10 miles
    private static final double MAX_DELIVERY_MILES = 10.0;

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final DeliveryZoneRepository deliveryZoneRepository;
    private final PickupRedemptionLogRepository redemptionLogRepository;
    private final SiteAuthorizationService siteAuth;
    private final SecureRandom secureRandom = new SecureRandom();

    @Audited(action = "CREATE", entityType = "Order")
    @Transactional
    public OrderResponse createOrder(Long customerId, OrderRequest request) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        Organization site = organizationRepository.findById(request.getSiteId())
                .orElseThrow(() -> new IllegalArgumentException("Site not found"));

        // Enforce site access — customers can only create orders at accessible sites
        siteAuth.requireSiteAccess(request.getSiteId());

        BigDecimal deliveryFee = BigDecimal.ZERO;
        Double distanceMiles = null;
        FulfillmentMode mode = request.getFulfillmentMode();

        // Mode-specific validation
        if (mode == FulfillmentMode.DELIVERY || mode == FulfillmentMode.COURIER_HANDOFF) {
            if (request.getDeliveryZip() == null || request.getDeliveryZip().isBlank()) {
                throw new IllegalArgumentException("Delivery ZIP code required for " + mode + " orders");
            }

            DeliveryZone zone = deliveryZoneRepository
                    .findBySiteIdAndZipCode(request.getSiteId(), request.getDeliveryZip())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Delivery not available to ZIP: " + request.getDeliveryZip()));

            if (!zone.isActive()) {
                throw new IllegalArgumentException("Delivery zone is currently inactive");
            }
            if (zone.getDistanceMiles() > MAX_DELIVERY_MILES) {
                throw new IllegalArgumentException("Delivery distance exceeds maximum range of 10 miles");
            }

            distanceMiles = zone.getDistanceMiles();
            deliveryFee = zone.getDeliveryFee() != null
                    ? zone.getDeliveryFee()
                    : calculateDefaultFee(distanceMiles);
        }

        BigDecimal total = request.getSubtotal().add(deliveryFee);
        String verificationCode = request.isPickup() ? generateVerificationCode() : null;

        Order order = Order.builder()
                .customer(customer)
                .site(site)
                .status(OrderStatus.PENDING)
                .subtotal(request.getSubtotal())
                .deliveryFee(deliveryFee)
                .total(total)
                .deliveryZip(request.getDeliveryZip())
                .deliveryDistanceMiles(distanceMiles)
                .fulfillmentMode(mode)
                .pickup(request.isPickup())
                .pickupVerificationCode(verificationCode)
                .courierNotes(request.getCourierNotes())
                .build();

        order = orderRepository.save(order);
        log.info("Order created: orderId={}, customerId={}, siteId={}, pickup={}, total={}", order.getId(), customerId, request.getSiteId(), request.isPickup(), total);
        return toResponse(order);
    }

    @Audited(action = "STATUS_UPDATE", entityType = "Order")
    @Transactional
    public OrderResponse updateStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        siteAuth.requireSiteAccess(order.getSite().getId());
        order.setStatus(newStatus);
        log.info("Order status updated: orderId={}, newStatus={}", orderId, newStatus);
        return toResponse(orderRepository.save(order));
    }

    @Audited(action = "VERIFY_PICKUP", entityType = "Order")
    @Transactional
    public OrderResponse verifyPickup(Long orderId, String code, Long requestingUserId, String requestingRole) {
        Order order = orderRepository.findByIdAndPickupVerificationCode(orderId, code)
                .orElseThrow(() -> new IllegalArgumentException("Invalid order ID or verification code"));

        // Customer cannot self-redeem their own pickup code
        if (order.getCustomer().getId().equals(requestingUserId)) {
            logRedemptionAttempt(order, requestingUserId, "DENIED_CUSTOMER_SELF", "Customer cannot self-redeem");
            throw new com.eaglepoint.storehub.config.AccessDeniedException(
                    "Pickup verification must be performed by staff, not the order customer");
        }

        // Verifier must have site access
        siteAuth.requireSiteAccess(order.getSite().getId());

        if (order.getFulfillmentMode() != FulfillmentMode.PICKUP && !order.isPickup()) {
            logRedemptionAttempt(order, requestingUserId, "DENIED_NOT_PICKUP", "Order is not a pickup order");
            throw new IllegalArgumentException("Order is not a pickup order");
        }
        if (order.isPickupVerified()) {
            logRedemptionAttempt(order, requestingUserId, "DENIED_ALREADY_VERIFIED", "Pickup already verified");
            throw new IllegalArgumentException("Pickup already verified");
        }

        order.setPickupVerified(true);
        order.setStatus(OrderStatus.PICKED_UP);
        logRedemptionAttempt(order, requestingUserId, "SUCCESS", null);

        User verifier = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new IllegalArgumentException("Verifier not found"));
        order.setVerifiedBy(verifier);
        order.setVerifiedAt(Instant.now());

        log.info("Pickup verified: orderId={}, verifiedBy={}", orderId, requestingUserId);
        return toResponse(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByCustomer(Long customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<OrderResponse> getOrdersByCustomer(Long customerId, org.springframework.data.domain.Pageable pageable) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable)
                .map(this::toResponse);
    }

    @DataScope
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersBySite(Long siteId) {
        List<Long> visibleSites = DataScopeContext.get();
        if (visibleSites != null && !visibleSites.contains(siteId)) {
            throw new com.eaglepoint.storehub.config.AccessDeniedException("Access denied: site not in your data scope");
        }
        List<Order> orders = orderRepository.findBySiteIdOrderByCreatedAtDesc(siteId);
        // Multi-dimensional scope enforcement
        Long teamId = DataScopeContext.getTeamId();
        if (teamId != null) {
            // Staff with team assignment: see only orders assigned to them or unassigned at their site
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.eaglepoint.storehub.security.UserPrincipal p) {
                Long staffId = p.getId();
                orders = orders.stream().filter(o ->
                        o.getAssignedStaff() == null // unassigned orders visible to all site staff
                        || o.getAssignedStaff().getId().equals(staffId) // assigned to this staff
                        || o.getVerifiedBy() != null && o.getVerifiedBy().getId().equals(staffId) // verified by this staff
                ).toList();
            }
        }
        return orders.stream().map(o -> toResponse(o, false)).toList();
    }

    @DataScope
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<OrderResponse> getOrdersBySite(Long siteId, org.springframework.data.domain.Pageable pageable) {
        List<Long> visibleSites = DataScopeContext.get();
        if (visibleSites != null && !visibleSites.contains(siteId)) {
            throw new com.eaglepoint.storehub.config.AccessDeniedException("Access denied: site not in your data scope");
        }
        // Multi-dimensional scope: team-scoped staff sees only assigned/unassigned orders
        Long teamId = DataScopeContext.getTeamId();
        if (teamId != null) {
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.eaglepoint.storehub.security.UserPrincipal p) {
                return orderRepository.findBySiteIdScopedToStaff(siteId, p.getId(), pageable)
                        .map(o -> toResponse(o, false));
            }
        }
        return orderRepository.findBySiteIdOrderByCreatedAtDesc(siteId, pageable)
                .map(o -> toResponse(o, false));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId, Long requestingUserId, String requestingRole) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        siteAuth.requireOwnerOrSiteAccess(order.getCustomer().getId(), order.getSite().getId());
        boolean isOwner = order.getCustomer().getId().equals(requestingUserId);
        return toResponse(order, isOwner);
    }

    private void logRedemptionAttempt(Order order, Long verifierId, String outcome, String reason) {
        User verifier = userRepository.findById(verifierId).orElse(null);
        if (verifier != null) {
            redemptionLogRepository.save(PickupRedemptionLog.builder()
                    .order(order).verifier(verifier).outcome(outcome).reason(reason).build());
        }
    }

    private BigDecimal calculateDefaultFee(double distanceMiles) {
        return distanceMiles <= 5.0 ? SHORT_RANGE_FEE : LONG_RANGE_FEE;
    }

    private String generateVerificationCode() {
        int code = 100000 + secureRandom.nextInt(900000);
        return String.valueOf(code);
    }

    private OrderResponse toResponse(Order order) {
        return toResponse(order, true);
    }

    private OrderResponse toResponse(Order order, boolean includeVerificationCode) {
        OrderResponse r = new OrderResponse();
        r.setId(order.getId());
        r.setCustomerId(order.getCustomer().getId());
        r.setSiteId(order.getSite().getId());
        r.setStatus(order.getStatus());
        r.setFulfillmentMode(order.getFulfillmentMode());
        r.setSubtotal(order.getSubtotal());
        r.setDeliveryFee(order.getDeliveryFee());
        r.setTotal(order.getTotal());
        r.setDeliveryZip(order.getDeliveryZip());
        r.setDeliveryDistanceMiles(order.getDeliveryDistanceMiles());
        r.setPickup(order.isPickup());
        r.setPickupVerificationCode(includeVerificationCode ? order.getPickupVerificationCode() : null);
        r.setPickupVerified(order.isPickupVerified());
        r.setCourierNotes(order.getCourierNotes());
        r.setVerifiedById(order.getVerifiedBy() != null ? order.getVerifiedBy().getId() : null);
        r.setVerifiedAt(order.getVerifiedAt());
        r.setCreatedAt(order.getCreatedAt());
        r.setUpdatedAt(order.getUpdatedAt());
        return r;
    }
}
