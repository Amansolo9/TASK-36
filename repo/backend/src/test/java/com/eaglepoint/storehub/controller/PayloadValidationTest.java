package com.eaglepoint.storehub.controller;

import com.eaglepoint.storehub.config.GlobalExceptionHandler;
import com.eaglepoint.storehub.dto.*;
import com.eaglepoint.storehub.entity.Organization;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.enums.*;
import com.eaglepoint.storehub.repository.*;
import com.eaglepoint.storehub.security.UserPrincipal;
import com.eaglepoint.storehub.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Explicit payload validation tests, separated from authorization tests.
 * These prove that invalid DTO payloads are rejected with 400,
 * independently of role/auth behavior.
 */
@WebMvcTest(controllers = {
        OrderController.class,
        RatingController.class,
        CommunityController.class,
        SupportTicketController.class
})
@Import({PayloadValidationTest.TestSecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "app.security.recent-auth-window-ms=600000"
})
class PayloadValidationTest {

    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
            return http.build();
        }
    }

    @Autowired private MockMvc mockMvc;

    @MockBean private OrderService orderService;
    @MockBean private ShippingLabelService shippingLabelService;
    @MockBean private SiteAuthorizationService siteAuth;
    @MockBean private RatingService ratingService;
    @MockBean private CommunityService communityService;
    @MockBean private GamificationService gamificationService;
    @MockBean private FavoriteService favoriteService;
    @MockBean private UserFollowService userFollowService;
    @MockBean private SupportTicketService ticketService;
    @MockBean private EvidenceService evidenceService;
    @MockBean private UserRepository userRepository;
    @MockBean private OrderRepository orderRepository;
    @MockBean private SupportTicketRepository supportTicketRepository;
    @MockBean private CheckInRepository checkInRepository;

    @Nested
    @DisplayName("Rating payload validation")
    class RatingValidation {

        @Test
        @DisplayName("Missing required rating fields → 400")
        void rating_missingFields_400() throws Exception {
            // Missing ratedUserId, targetType, stars, and score fields
            mockMvc.perform(post("/api/ratings")
                    .with(authentication(authFor(Role.CUSTOMER)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"orderId\":1}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Valid rating payload → 200")
        void rating_validPayload_200() throws Exception {
            when(ratingService.submitRating(anyLong(), any())).thenReturn(new RatingResponse());
            mockMvc.perform(post("/api/ratings")
                    .with(authentication(authFor(Role.CUSTOMER)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"orderId\":1,\"ratedUserId\":2,\"targetType\":\"STAFF\",\"stars\":5,\"timelinessScore\":5,\"communicationScore\":5,\"accuracyScore\":5}"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Order payload validation")
    class OrderValidation {

        @Test
        @DisplayName("Missing siteId in order → 400")
        void order_missingSiteId_400() throws Exception {
            mockMvc.perform(post("/api/orders")
                    .with(authentication(authFor(Role.CUSTOMER)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"subtotal\":10.00,\"fulfillmentMode\":\"PICKUP\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Valid order payload → 200")
        void order_validPayload_200() throws Exception {
            when(orderService.createOrder(anyLong(), any())).thenReturn(new OrderResponse());
            mockMvc.perform(post("/api/orders")
                    .with(authentication(authFor(Role.CUSTOMER)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"siteId\":1,\"subtotal\":10.00,\"fulfillmentMode\":\"PICKUP\"}"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Community post payload validation")
    class CommunityPostValidation {

        @Test
        @DisplayName("Missing body field in post → 400")
        void post_missingBody_400() throws Exception {
            mockMvc.perform(post("/api/community/posts")
                    .with(authentication(authFor(Role.CUSTOMER)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"test\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Valid post payload → 200")
        void post_validPayload_200() throws Exception {
            when(communityService.createPost(anyLong(), any())).thenReturn(new PostResponse());
            mockMvc.perform(post("/api/community/posts")
                    .with(authentication(authFor(Role.CUSTOMER)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"test\",\"body\":\"test body\"}"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Ticket payload validation")
    class TicketValidation {

        @Test
        @DisplayName("Missing description in ticket → 400")
        void ticket_missingDescription_400() throws Exception {
            mockMvc.perform(post("/api/tickets")
                    .with(authentication(authFor(Role.CUSTOMER)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"orderId\":1,\"type\":\"REFUND_ONLY\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Valid ticket payload → 200")
        void ticket_validPayload_200() throws Exception {
            when(ticketService.createTicket(anyLong(), any())).thenReturn(new TicketResponse());
            mockMvc.perform(post("/api/tickets")
                    .with(authentication(authFor(Role.CUSTOMER)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"orderId\":1,\"type\":\"REFUND_ONLY\",\"description\":\"Item was damaged\"}"))
                    .andExpect(status().isOk());
        }
    }

    private static Authentication authFor(Role role) {
        User user = User.builder()
                .id(1L).username("testuser").email("test@test.com")
                .passwordHash("hashed").role(role).enabled(true).tokenVersion(0)
                .site(Organization.builder().id(10L).name("TestSite").build())
                .build();
        UserPrincipal principal = new UserPrincipal(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
