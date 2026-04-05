package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.annotation.Audited;
import com.eaglepoint.storehub.annotation.DataScope;
import com.eaglepoint.storehub.aspect.DataScopeContext;
import com.eaglepoint.storehub.dto.EvidenceDto;
import com.eaglepoint.storehub.dto.TicketRequest;
import com.eaglepoint.storehub.dto.TicketResponse;
import com.eaglepoint.storehub.entity.EvidenceFile;
import com.eaglepoint.storehub.entity.Order;
import com.eaglepoint.storehub.entity.SupportTicket;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.enums.TicketStatus;
import com.eaglepoint.storehub.enums.TicketType;
import com.eaglepoint.storehub.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupportTicketService {

    private static final BigDecimal AUTO_APPROVE_THRESHOLD = new BigDecimal("25.00");
    private static final Duration SLA_FIRST_RESPONSE = Duration.ofHours(8);
    private static final Duration RETURN_ELIGIBILITY_WINDOW = Duration.ofDays(14);

    private final SupportTicketRepository ticketRepository;
    private final EvidenceFileRepository evidenceFileRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final SiteAuthorizationService siteAuth;
    private final CreditScoreService creditScoreService;

    @Audited(action = "CREATE", entityType = "SupportTicket")
    @Transactional
    public TicketResponse createTicket(Long customerId, TicketRequest request) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (!order.getCustomer().getId().equals(customerId)) {
            throw new IllegalArgumentException("Order does not belong to this customer");
        }

        // M2: Enforce 14-day return eligibility window for RETURN_AND_REFUND
        if (request.getType() == TicketType.RETURN_AND_REFUND) {
            Instant cutoff = Instant.now().minus(RETURN_ELIGIBILITY_WINDOW);
            if (order.getCreatedAt().isBefore(cutoff)) {
                throw new IllegalArgumentException("Return requests must be filed within 14 days of order placement");
            }
        }

        BigDecimal refundAmount = request.getRefundAmount() != null
                ? request.getRefundAmount() : order.getTotal();

        Instant now = Instant.now();

        SupportTicket ticket = SupportTicket.builder()
                .order(order)
                .customer(customer)
                .type(request.getType())
                .status(TicketStatus.OPEN)
                .description(request.getDescription())
                .refundAmount(refundAmount)
                .firstResponseDueAt(calculateSlaDue(now))
                .build();

        // Auto-approve: orders < $25 with no prior abuse
        if (refundAmount.compareTo(AUTO_APPROVE_THRESHOLD) < 0) {
            long priorTickets = ticketRepository.countPriorApprovedTickets(customerId);
            if (priorTickets == 0) {
                ticket.setAutoApproved(true);
                ticket.setStatus(TicketStatus.APPROVED);
                ticket.setFirstResponseAt(now);
                log.info("Ticket auto-approved for customerId={}, refundAmount={}", customerId, refundAmount);
            }
        }

        ticket = ticketRepository.save(ticket);
        log.info("Support ticket created: ticketId={}, customerId={}, orderId={}, type={}, status={}", ticket.getId(), customerId, request.getOrderId(), request.getType(), ticket.getStatus());
        return toResponse(ticket);
    }

    @Audited(action = "STATUS_UPDATE", entityType = "SupportTicket")
    @Transactional
    public TicketResponse updateStatus(Long ticketId, TicketStatus newStatus) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
        siteAuth.requireSiteAccess(ticket.getOrder().getSite().getId());

        if (ticket.getFirstResponseAt() == null && isResponseStatus(newStatus)) {
            ticket.setFirstResponseAt(Instant.now());
        }

        ticket.setStatus(newStatus);

        // Trigger dispute credit impact on refund approval
        if (newStatus == TicketStatus.APPROVED || newStatus == TicketStatus.REFUNDED) {
            Long customerId = ticket.getCustomer().getId();
            long approvedTickets = ticketRepository.countPriorApprovedTickets(customerId);
            creditScoreService.updateFromDispute(customerId, (int) approvedTickets);
        }

        log.info("Ticket status updated: ticketId={}, newStatus={}", ticketId, newStatus);
        return toResponse(ticketRepository.save(ticket));
    }

    @Audited(action = "ASSIGN", entityType = "SupportTicket")
    @Transactional
    public TicketResponse assignTicket(Long ticketId, Long staffId) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
        siteAuth.requireSiteAccess(ticket.getOrder().getSite().getId());
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Staff not found"));

        // Validate assignee has an operational role (not CUSTOMER)
        if (staff.getRole() == com.eaglepoint.storehub.enums.Role.CUSTOMER) {
            throw new IllegalArgumentException("Cannot assign ticket to a customer; assignee must be staff or manager");
        }

        // Validate assignee belongs to the same site
        if (staff.getSite() != null && !staff.getSite().getId().equals(ticket.getOrder().getSite().getId())) {
            throw new IllegalArgumentException("Assignee must belong to the same site as the ticket");
        }

        ticket.setAssignedTo(staff);
        if (ticket.getStatus() == TicketStatus.OPEN) {
            ticket.setStatus(TicketStatus.UNDER_REVIEW);
        }
        return toResponse(ticketRepository.save(ticket));
    }

    @Transactional(readOnly = true)
    public TicketResponse getTicket(Long ticketId, Long requestingUserId, String requestingRole) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
        siteAuth.requireOwnerOrSiteAccess(ticket.getCustomer().getId(), ticket.getOrder().getSite().getId());
        return toResponse(ticket);
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> getMyTickets(Long customerId) {
        return ticketRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(this::toResponse).toList();
    }

    @DataScope
    @Transactional(readOnly = true)
    public List<TicketResponse> getTicketsByStatus(TicketStatus status) {
        List<Long> visibleSites = DataScopeContext.get();
        return ticketRepository.findByStatusOrderByCreatedAtAsc(status).stream()
                .filter(t -> visibleSites == null || visibleSites.contains(t.getOrder().getSite().getId()))
                .map(this::toResponse).toList();
    }

    private boolean isResponseStatus(TicketStatus status) {
        return status == TicketStatus.UNDER_REVIEW
                || status == TicketStatus.APPROVED
                || status == TicketStatus.REJECTED
                || status == TicketStatus.AWAITING_EVIDENCE;
    }

    /**
     * Calculate SLA due time: 8 business hours from the given instant.
     * Business hours: Mon-Fri, 9 AM - 5 PM (system default timezone).
     * Skips weekends. Hours outside 9-17 roll to next business day opening.
     */
    private Instant calculateSlaDue(Instant from) {
        java.time.ZonedDateTime zdt = from.atZone(java.time.ZoneId.systemDefault());
        long remainingMinutes = SLA_FIRST_RESPONSE.toMinutes();

        while (remainingMinutes > 0) {
            java.time.DayOfWeek dow = zdt.getDayOfWeek();
            // Skip weekends
            if (dow == java.time.DayOfWeek.SATURDAY) {
                zdt = zdt.plusDays(2).withHour(9).withMinute(0).withSecond(0).withNano(0);
                continue;
            }
            if (dow == java.time.DayOfWeek.SUNDAY) {
                zdt = zdt.plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
                continue;
            }
            // Before business hours
            if (zdt.getHour() < 9) {
                zdt = zdt.withHour(9).withMinute(0).withSecond(0).withNano(0);
            }
            // After business hours
            if (zdt.getHour() >= 17) {
                zdt = zdt.plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
                continue;
            }
            // Minutes left in current business day
            long endOfDayMinutes = (17 - zdt.getHour()) * 60L - zdt.getMinute();
            if (remainingMinutes <= endOfDayMinutes) {
                zdt = zdt.plusMinutes(remainingMinutes);
                remainingMinutes = 0;
            } else {
                remainingMinutes -= endOfDayMinutes;
                zdt = zdt.plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
            }
        }
        return zdt.toInstant();
    }

    private TicketResponse toResponse(SupportTicket t) {
        TicketResponse r = new TicketResponse();
        r.setId(t.getId());
        r.setOrderId(t.getOrder().getId());
        r.setCustomerId(t.getCustomer().getId());
        r.setCustomerName(t.getCustomer().getUsername());
        r.setAssignedToId(t.getAssignedTo() != null ? t.getAssignedTo().getId() : null);
        r.setType(t.getType());
        r.setStatus(t.getStatus());
        r.setDescription(t.getDescription());
        r.setRefundAmount(t.getRefundAmount());
        r.setAutoApproved(t.isAutoApproved());
        r.setSlaBreached(t.isSlaBreached());
        r.setFirstResponseDueAt(t.getFirstResponseDueAt());
        r.setCreatedAt(t.getCreatedAt());
        r.setUpdatedAt(t.getUpdatedAt());

        List<EvidenceFile> files = evidenceFileRepository.findByTicketId(t.getId());
        r.setEvidence(files.stream().map(this::toEvidenceDto).toList());

        return r;
    }

    private EvidenceDto toEvidenceDto(EvidenceFile f) {
        EvidenceDto d = new EvidenceDto();
        d.setId(f.getId());
        d.setFileName(f.getFileName());
        d.setContentType(f.getContentType());
        d.setFileSize(f.getFileSize());
        d.setSha256Hash(f.getSha256Hash());
        d.setCreatedAt(f.getCreatedAt());
        return d;
    }
}
