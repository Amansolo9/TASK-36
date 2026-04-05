package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.dto.TicketRequest;
import com.eaglepoint.storehub.dto.TicketResponse;
import com.eaglepoint.storehub.entity.Order;
import com.eaglepoint.storehub.entity.Organization;
import com.eaglepoint.storehub.entity.SupportTicket;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.enums.OrderStatus;
import com.eaglepoint.storehub.enums.Role;
import com.eaglepoint.storehub.enums.TicketStatus;
import com.eaglepoint.storehub.enums.TicketType;
import com.eaglepoint.storehub.repository.EvidenceFileRepository;
import com.eaglepoint.storehub.repository.OrderRepository;
import com.eaglepoint.storehub.repository.SupportTicketRepository;
import com.eaglepoint.storehub.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupportTicketServiceTest {

    @Mock
    private SupportTicketRepository ticketRepository;

    @Mock
    private EvidenceFileRepository evidenceFileRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SiteAuthorizationService siteAuth;

    @InjectMocks
    private SupportTicketService supportTicketService;

    private User testCustomer;
    private Organization testSite;
    private Order testOrder;

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

        testOrder = Order.builder()
                .id(1L)
                .customer(testCustomer)
                .site(testSite)
                .status(OrderStatus.PENDING)
                .subtotal(new BigDecimal("20.00"))
                .deliveryFee(BigDecimal.ZERO)
                .total(new BigDecimal("20.00"))
                .build();
    }

    private TicketRequest createTicketRequest(BigDecimal refundAmount) {
        TicketRequest request = new TicketRequest();
        request.setOrderId(1L);
        request.setType(TicketType.REFUND_ONLY);
        request.setDescription("Item was damaged");
        request.setRefundAmount(refundAmount);
        return request;
    }

    @Test
    void createTicket_autoApprove_orderUnder25_noPriorTickets() {
        TicketRequest request = createTicketRequest(new BigDecimal("15.00"));

        when(userRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(ticketRepository.countPriorApprovedTickets(1L)).thenReturn(0L);
        when(evidenceFileRepository.findByTicketId(any())).thenReturn(Collections.emptyList());
        when(ticketRepository.save(any(SupportTicket.class))).thenAnswer(invocation -> {
            SupportTicket t = invocation.getArgument(0);
            t.setId(1L);
            return t;
        });

        TicketResponse response = supportTicketService.createTicket(1L, request);

        assertNotNull(response);
        assertTrue(response.isAutoApproved());
        assertEquals(TicketStatus.APPROVED, response.getStatus());
    }

    @Test
    void createTicket_noAutoApprove_orderAtOrAbove25() {
        TicketRequest request = createTicketRequest(new BigDecimal("25.00"));

        when(userRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(evidenceFileRepository.findByTicketId(any())).thenReturn(Collections.emptyList());
        when(ticketRepository.save(any(SupportTicket.class))).thenAnswer(invocation -> {
            SupportTicket t = invocation.getArgument(0);
            t.setId(2L);
            return t;
        });

        TicketResponse response = supportTicketService.createTicket(1L, request);

        assertNotNull(response);
        assertFalse(response.isAutoApproved());
        assertEquals(TicketStatus.OPEN, response.getStatus());
    }

    @Test
    void createTicket_noAutoApprove_priorTicketsExist() {
        TicketRequest request = createTicketRequest(new BigDecimal("10.00"));

        when(userRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(ticketRepository.countPriorApprovedTickets(1L)).thenReturn(1L);
        when(evidenceFileRepository.findByTicketId(any())).thenReturn(Collections.emptyList());
        when(ticketRepository.save(any(SupportTicket.class))).thenAnswer(invocation -> {
            SupportTicket t = invocation.getArgument(0);
            t.setId(3L);
            return t;
        });

        TicketResponse response = supportTicketService.createTicket(1L, request);

        assertNotNull(response);
        assertFalse(response.isAutoApproved());
        assertEquals(TicketStatus.OPEN, response.getStatus());
    }

    @Test
    void createTicket_setsSlaDueTime() {
        TicketRequest request = createTicketRequest(new BigDecimal("50.00"));

        when(userRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(evidenceFileRepository.findByTicketId(any())).thenReturn(Collections.emptyList());
        when(ticketRepository.save(any(SupportTicket.class))).thenAnswer(invocation -> {
            SupportTicket t = invocation.getArgument(0);
            t.setId(4L);
            return t;
        });

        TicketResponse response = supportTicketService.createTicket(1L, request);

        assertNotNull(response);
        assertNotNull(response.getFirstResponseDueAt());
        // SLA is 8 business hours from creation; the due time should be in the future
        assertTrue(response.getFirstResponseDueAt().isAfter(Instant.now()));
        // Business-hours SLA can span multiple calendar days (weekends, after-hours);
        // verify it's within a reasonable bound of 5 calendar days
        assertTrue(response.getFirstResponseDueAt().isBefore(
                Instant.now().plus(Duration.ofDays(5))));
    }

    @Test
    void updateStatus_recordsFirstResponseTime() {
        SupportTicket existingTicket = SupportTicket.builder()
                .id(5L)
                .order(testOrder)
                .customer(testCustomer)
                .type(TicketType.REFUND_ONLY)
                .status(TicketStatus.OPEN)
                .description("Item damaged")
                .refundAmount(new BigDecimal("20.00"))
                .firstResponseAt(null)
                .firstResponseDueAt(Instant.now().plus(Duration.ofHours(8)))
                .build();

        when(ticketRepository.findById(5L)).thenReturn(Optional.of(existingTicket));
        when(evidenceFileRepository.findByTicketId(5L)).thenReturn(Collections.emptyList());
        when(ticketRepository.save(any(SupportTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponse response = supportTicketService.updateStatus(5L, TicketStatus.UNDER_REVIEW);

        assertNotNull(response);
        assertEquals(TicketStatus.UNDER_REVIEW, response.getStatus());
        // Verify that firstResponseAt was set on the ticket entity
        assertNotNull(existingTicket.getFirstResponseAt());
    }
}
