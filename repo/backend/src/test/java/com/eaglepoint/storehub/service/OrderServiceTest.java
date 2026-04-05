package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.dto.OrderRequest;
import com.eaglepoint.storehub.dto.OrderResponse;
import com.eaglepoint.storehub.entity.DeliveryZone;
import com.eaglepoint.storehub.entity.Order;
import com.eaglepoint.storehub.entity.Organization;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.enums.FulfillmentMode;
import com.eaglepoint.storehub.enums.OrderStatus;
import com.eaglepoint.storehub.enums.Role;
import com.eaglepoint.storehub.repository.DeliveryZoneRepository;
import com.eaglepoint.storehub.repository.OrderRepository;
import com.eaglepoint.storehub.repository.OrganizationRepository;
import com.eaglepoint.storehub.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private DeliveryZoneRepository deliveryZoneRepository;

    @Mock
    private SiteAuthorizationService siteAuth;

    @Mock
    private com.eaglepoint.storehub.repository.PickupRedemptionLogRepository redemptionLogRepository;

    @InjectMocks
    private OrderService orderService;

    private User testCustomer;
    private Organization testSite;

    @BeforeEach
    void setUp() {
        testSite = Organization.builder()
                .id(1L)
                .name("Test Site")
                .build();

        testCustomer = User.builder()
                .id(1L)
                .username("customer")
                .email("customer@example.com")
                .passwordHash("encoded")
                .role(Role.CUSTOMER)
                .site(testSite)
                .build();
    }

    private OrderRequest createDeliveryRequest(String zip) {
        OrderRequest request = new OrderRequest();
        request.setSiteId(1L);
        request.setSubtotal(new BigDecimal("20.00"));
        request.setFulfillmentMode(FulfillmentMode.DELIVERY);
        request.setDeliveryZip(zip);
        return request;
    }

    private OrderRequest createPickupRequest() {
        OrderRequest request = new OrderRequest();
        request.setSiteId(1L);
        request.setSubtotal(new BigDecimal("15.00"));
        request.setFulfillmentMode(FulfillmentMode.PICKUP);
        return request;
    }

    private void setupCommonMocks() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(testSite));
    }

    @Test
    void createOrder_deliveryShortRange_correctFee() {
        setupCommonMocks();
        OrderRequest request = createDeliveryRequest("12345");

        DeliveryZone zone = DeliveryZone.builder()
                .id(1L)
                .site(testSite)
                .zipCode("12345")
                .distanceMiles(3.0)
                .deliveryFee(null)  // Use default fee calculation
                .active(true)
                .build();

        when(deliveryZoneRepository.findBySiteIdAndZipCode(1L, "12345"))
                .thenReturn(Optional.of(zone));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(1L);
            return o;
        });

        OrderResponse response = orderService.createOrder(1L, request);

        assertNotNull(response);
        assertEquals(new BigDecimal("4.99"), response.getDeliveryFee());
        assertEquals(new BigDecimal("24.99"), response.getTotal());
        assertFalse(response.isPickup());
    }

    @Test
    void createOrder_deliveryLongRange_correctFee() {
        setupCommonMocks();
        OrderRequest request = createDeliveryRequest("67890");

        DeliveryZone zone = DeliveryZone.builder()
                .id(2L)
                .site(testSite)
                .zipCode("67890")
                .distanceMiles(7.5)
                .deliveryFee(null)  // Use default fee calculation
                .active(true)
                .build();

        when(deliveryZoneRepository.findBySiteIdAndZipCode(1L, "67890"))
                .thenReturn(Optional.of(zone));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(2L);
            return o;
        });

        OrderResponse response = orderService.createOrder(1L, request);

        assertNotNull(response);
        assertEquals(new BigDecimal("7.99"), response.getDeliveryFee());
        assertEquals(new BigDecimal("27.99"), response.getTotal());
    }

    @Test
    void createOrder_pickup_generatesVerificationCode() {
        setupCommonMocks();
        OrderRequest request = createPickupRequest();

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(3L);
            return o;
        });

        OrderResponse response = orderService.createOrder(1L, request);

        assertNotNull(response);
        assertTrue(response.isPickup());
        assertNotNull(response.getPickupVerificationCode());
        assertEquals(6, response.getPickupVerificationCode().length());
        assertTrue(response.getPickupVerificationCode().matches("\\d{6}"));
        assertEquals(BigDecimal.ZERO, response.getDeliveryFee());
    }

    @Test
    void createOrder_deliveryMissingZip_throwsException() {
        setupCommonMocks();
        OrderRequest request = new OrderRequest();
        request.setSiteId(1L);
        request.setSubtotal(new BigDecimal("20.00"));
        request.setFulfillmentMode(FulfillmentMode.DELIVERY);
        request.setDeliveryZip(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderService.createOrder(1L, request));
        assertEquals("Delivery ZIP code required for DELIVERY orders", ex.getMessage());
    }

    @Test
    void createOrder_deliveryBeyond10Miles_throwsException() {
        setupCommonMocks();
        OrderRequest request = createDeliveryRequest("99999");

        DeliveryZone zone = DeliveryZone.builder()
                .id(3L)
                .site(testSite)
                .zipCode("99999")
                .distanceMiles(12.0)
                .deliveryFee(null)
                .active(true)
                .build();

        when(deliveryZoneRepository.findBySiteIdAndZipCode(1L, "99999"))
                .thenReturn(Optional.of(zone));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderService.createOrder(1L, request));
        assertEquals("Delivery distance exceeds maximum range of 10 miles", ex.getMessage());
    }

    @Test
    void verifyPickup_correctCode_succeeds() {
        User staffUser = User.builder()
                .id(5L)
                .username("staff")
                .email("staff@example.com")
                .passwordHash("encoded")
                .role(Role.STAFF)
                .site(testSite)
                .build();

        Order pickupOrder = Order.builder()
                .id(10L)
                .customer(testCustomer)
                .site(testSite)
                .status(OrderStatus.PENDING)
                .subtotal(new BigDecimal("15.00"))
                .deliveryFee(BigDecimal.ZERO)
                .total(new BigDecimal("15.00"))
                .pickup(true)
                .pickupVerificationCode("123456")
                .pickupVerified(false)
                .build();

        when(orderRepository.findByIdAndPickupVerificationCode(10L, "123456"))
                .thenReturn(Optional.of(pickupOrder));
        when(userRepository.findById(5L)).thenReturn(Optional.of(staffUser));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.verifyPickup(10L, "123456", 5L, "STAFF");

        assertNotNull(response);
        assertTrue(response.isPickupVerified());
        assertEquals(OrderStatus.PICKED_UP, response.getStatus());
    }

    @Test
    void verifyPickup_customerSelfRedeem_denied() {
        Order pickupOrder = Order.builder()
                .id(10L).customer(testCustomer).site(testSite)
                .status(OrderStatus.PENDING)
                .subtotal(new BigDecimal("20.00"))
                .deliveryFee(BigDecimal.ZERO)
                .total(new BigDecimal("20.00"))
                .pickup(true).pickupVerificationCode("123456").pickupVerified(false)
                .build();

        when(orderRepository.findByIdAndPickupVerificationCode(10L, "123456"))
                .thenReturn(Optional.of(pickupOrder));

        assertThrows(com.eaglepoint.storehub.config.AccessDeniedException.class,
                () -> orderService.verifyPickup(10L, "123456", 1L, "CUSTOMER"));
    }

    @Test
    void verifyPickup_wrongCode_throwsException() {
        when(orderRepository.findByIdAndPickupVerificationCode(10L, "000000"))
                .thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderService.verifyPickup(10L, "000000", 1L, "CUSTOMER"));
        assertEquals("Invalid order ID or verification code", ex.getMessage());
    }

    @Test
    void verifyPickup_notPickupOrder_throwsException() {
        Order deliveryOrder = Order.builder()
                .id(11L)
                .customer(testCustomer)
                .site(testSite)
                .status(OrderStatus.PENDING)
                .subtotal(new BigDecimal("20.00"))
                .deliveryFee(new BigDecimal("4.99"))
                .total(new BigDecimal("24.99"))
                .pickup(false)
                .pickupVerificationCode("123456")
                .pickupVerified(false)
                .build();

        when(orderRepository.findByIdAndPickupVerificationCode(11L, "123456"))
                .thenReturn(Optional.of(deliveryOrder));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderService.verifyPickup(11L, "123456", 1L, "CUSTOMER"));
        assertEquals("Order is not a pickup order", ex.getMessage());
    }

    @Test
    void verifyPickup_alreadyVerified_throwsException() {
        Order verifiedOrder = Order.builder()
                .id(12L)
                .customer(testCustomer)
                .site(testSite)
                .status(OrderStatus.PICKED_UP)
                .subtotal(new BigDecimal("20.00"))
                .deliveryFee(BigDecimal.ZERO)
                .total(new BigDecimal("20.00"))
                .pickup(true)
                .pickupVerificationCode("123456")
                .pickupVerified(true)
                .build();

        when(orderRepository.findByIdAndPickupVerificationCode(12L, "123456"))
                .thenReturn(Optional.of(verifiedOrder));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderService.verifyPickup(12L, "123456", 1L, "CUSTOMER"));
        assertEquals("Pickup already verified", ex.getMessage());
    }
}
