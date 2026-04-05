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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FulfillmentModeTest {

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

    private void setupCommonMocks() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(testSite));
    }

    @Test
    void pickup_createsVerificationCode() {
        setupCommonMocks();

        OrderRequest request = new OrderRequest();
        request.setSiteId(1L);
        request.setSubtotal(new BigDecimal("15.00"));
        request.setFulfillmentMode(FulfillmentMode.PICKUP);

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(1L);
            return o;
        });

        OrderResponse response = orderService.createOrder(1L, request);

        assertNotNull(response);
        assertTrue(response.isPickup());
        assertNotNull(response.getPickupVerificationCode());
        assertEquals(6, response.getPickupVerificationCode().length());
        assertTrue(response.getPickupVerificationCode().matches("\\d{6}"));
        assertEquals(BigDecimal.ZERO, response.getDeliveryFee());
        assertEquals(FulfillmentMode.PICKUP, response.getFulfillmentMode());
    }

    @Test
    void delivery_requiresZip() {
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
    void courierHandoff_requiresZip() {
        setupCommonMocks();

        OrderRequest request = new OrderRequest();
        request.setSiteId(1L);
        request.setSubtotal(new BigDecimal("20.00"));
        request.setFulfillmentMode(FulfillmentMode.COURIER_HANDOFF);
        request.setDeliveryZip(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderService.createOrder(1L, request));
        assertEquals("Delivery ZIP code required for COURIER_HANDOFF orders", ex.getMessage());
    }

    @Test
    void courierHandoff_storesCourierNotes() {
        setupCommonMocks();

        OrderRequest request = new OrderRequest();
        request.setSiteId(1L);
        request.setSubtotal(new BigDecimal("25.00"));
        request.setFulfillmentMode(FulfillmentMode.COURIER_HANDOFF);
        request.setDeliveryZip("12345");
        request.setCourierNotes("Leave at front desk, ask for badge #42");

        DeliveryZone zone = DeliveryZone.builder()
                .id(1L)
                .site(testSite)
                .zipCode("12345")
                .distanceMiles(3.0)
                .deliveryFee(null)
                .active(true)
                .build();

        when(deliveryZoneRepository.findBySiteIdAndZipCode(1L, "12345"))
                .thenReturn(Optional.of(zone));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(2L);
            return o;
        });

        OrderResponse response = orderService.createOrder(1L, request);

        assertNotNull(response);
        assertEquals(FulfillmentMode.COURIER_HANDOFF, response.getFulfillmentMode());
        assertEquals("Leave at front desk, ask for badge #42", response.getCourierNotes());
        assertFalse(response.isPickup());
        assertNull(response.getPickupVerificationCode());
    }
}
