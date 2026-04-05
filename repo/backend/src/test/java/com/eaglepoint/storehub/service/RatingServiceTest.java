package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.dto.RatingRequest;
import com.eaglepoint.storehub.dto.RatingResponse;
import com.eaglepoint.storehub.entity.Order;
import com.eaglepoint.storehub.entity.Organization;
import com.eaglepoint.storehub.entity.Rating;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.enums.AppealStatus;
import com.eaglepoint.storehub.enums.OrderStatus;
import com.eaglepoint.storehub.enums.RatingTarget;
import com.eaglepoint.storehub.enums.Role;
import com.eaglepoint.storehub.repository.OrderRepository;
import com.eaglepoint.storehub.repository.RatingRepository;
import com.eaglepoint.storehub.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    @Mock
    private RatingRepository ratingRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CreditScoreService creditScoreService;

    @InjectMocks
    private RatingService ratingService;

    private User rater;
    private User ratedUser;
    private Order testOrder;
    private Organization testSite;

    @BeforeEach
    void setUp() {
        testSite = Organization.builder()
                .id(1L)
                .name("Test Site")
                .build();

        rater = User.builder()
                .id(1L)
                .username("rater")
                .email("rater@example.com")
                .passwordHash("encoded")
                .role(Role.CUSTOMER)
                .site(testSite)
                .build();

        ratedUser = User.builder()
                .id(2L)
                .username("staff")
                .email("staff@example.com")
                .passwordHash("encoded")
                .role(Role.STAFF)
                .site(testSite)
                .build();

        testOrder = Order.builder()
                .id(1L)
                .customer(rater)
                .assignedStaff(ratedUser)
                .site(testSite)
                .status(OrderStatus.PICKED_UP)
                .subtotal(new BigDecimal("20.00"))
                .deliveryFee(BigDecimal.ZERO)
                .total(new BigDecimal("20.00"))
                .build();
    }

    private RatingRequest createRatingRequest() {
        RatingRequest request = new RatingRequest();
        request.setOrderId(1L);
        request.setRatedUserId(2L);
        request.setTargetType(RatingTarget.STAFF);
        request.setStars(5);
        request.setComment("Excellent service!");
        return request;
    }

    @Test
    void submitRating_success() {
        RatingRequest request = createRatingRequest();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(userRepository.findById(1L)).thenReturn(Optional.of(rater));
        when(userRepository.findById(2L)).thenReturn(Optional.of(ratedUser));
        when(ratingRepository.findByOrderIdAndRaterId(1L, 1L)).thenReturn(Optional.empty());
        when(ratingRepository.findAverageStarsByRatedUserId(2L)).thenReturn(5.0);
        when(ratingRepository.save(any(Rating.class))).thenAnswer(invocation -> {
            Rating r = invocation.getArgument(0);
            r.setId(1L);
            r.setCreatedAt(Instant.now());
            return r;
        });

        RatingResponse response = ratingService.submitRating(1L, request);

        assertNotNull(response);
        assertEquals(5, response.getStars());
        assertEquals(1L, response.getRaterId());
        assertEquals(2L, response.getRatedUserId());
        assertEquals(RatingTarget.STAFF, response.getTargetType());
        verify(ratingRepository).save(any(Rating.class));
    }

    @Test
    void submitRating_duplicate_throwsException() {
        RatingRequest request = createRatingRequest();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(userRepository.findById(1L)).thenReturn(Optional.of(rater));
        when(userRepository.findById(2L)).thenReturn(Optional.of(ratedUser));
        when(ratingRepository.findByOrderIdAndRaterId(1L, 1L))
                .thenReturn(Optional.of(Rating.builder().id(99L).build()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ratingService.submitRating(1L, request));
        assertEquals("You have already rated this order", ex.getMessage());
    }

    @Test
    void submitRating_unrelatedStaff_denied() {
        User unrelatedStaff = User.builder()
                .id(99L)
                .username("unrelated")
                .email("unrelated@example.com")
                .passwordHash("encoded")
                .role(Role.STAFF)
                .site(testSite)
                .build();

        RatingRequest request = new RatingRequest();
        request.setOrderId(1L);
        request.setRatedUserId(1L);
        request.setTargetType(RatingTarget.CUSTOMER);
        request.setStars(4);
        request.setComment("Good customer");

        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(userRepository.findById(99L)).thenReturn(Optional.of(unrelatedStaff));
        when(userRepository.findById(1L)).thenReturn(Optional.of(rater));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ratingService.submitRating(99L, request));
        assertEquals("Rater is not a participant in this order", ex.getMessage());
    }

    @Test
    void submitAppeal_within7Days_succeeds() {
        Rating rating = Rating.builder()
                .id(1L)
                .order(testOrder)
                .rater(rater)
                .ratedUser(ratedUser)
                .targetType(RatingTarget.STAFF)
                .stars(1)
                .createdAt(Instant.now().minus(3, ChronoUnit.DAYS))
                .build();

        when(ratingRepository.findById(1L)).thenReturn(Optional.of(rating));
        when(ratingRepository.save(any(Rating.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // ratedUser (id=2) appeals
        RatingResponse response = ratingService.submitAppeal(1L, 2L, "Unfair rating");

        assertNotNull(response);
        assertEquals(AppealStatus.PENDING, response.getAppealStatus());
    }

    @Test
    void submitAppeal_after7Days_throwsException() {
        Rating rating = Rating.builder()
                .id(2L)
                .order(testOrder)
                .rater(rater)
                .ratedUser(ratedUser)
                .targetType(RatingTarget.STAFF)
                .stars(1)
                .createdAt(Instant.now().minus(10, ChronoUnit.DAYS))
                .build();

        when(ratingRepository.findById(2L)).thenReturn(Optional.of(rating));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ratingService.submitAppeal(2L, 2L, "Too late appeal"));
        assertEquals("Appeal window has expired (7 days)", ex.getMessage());
    }

    @Test
    void submitAppeal_notRatedUser_throwsException() {
        Rating rating = Rating.builder()
                .id(3L)
                .order(testOrder)
                .rater(rater)
                .ratedUser(ratedUser)
                .targetType(RatingTarget.STAFF)
                .stars(2)
                .createdAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        when(ratingRepository.findById(3L)).thenReturn(Optional.of(rating));

        // rater (id=1) tries to appeal, but only ratedUser (id=2) can
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ratingService.submitAppeal(3L, 1L, "Not my appeal"));
        assertEquals("Only the rated user can appeal", ex.getMessage());
    }

    @Test
    void submitRating_setsMultiDimensionalScores() {
        RatingRequest request = new RatingRequest();
        request.setOrderId(1L);
        request.setRatedUserId(2L);
        request.setTargetType(RatingTarget.STAFF);
        request.setStars(4);
        request.setTimelinessScore(5);
        request.setCommunicationScore(3);
        request.setAccuracyScore(4);
        request.setComment("Good service");

        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(userRepository.findById(1L)).thenReturn(Optional.of(rater));
        when(userRepository.findById(2L)).thenReturn(Optional.of(ratedUser));
        when(ratingRepository.findByOrderIdAndRaterId(1L, 1L)).thenReturn(Optional.empty());
        when(ratingRepository.findAverageStarsByRatedUserId(2L)).thenReturn(4.0);
        when(ratingRepository.save(any(Rating.class))).thenAnswer(invocation -> {
            Rating r = invocation.getArgument(0);
            r.setId(2L);
            r.setCreatedAt(Instant.now());
            return r;
        });

        RatingResponse response = ratingService.submitRating(1L, request);

        assertNotNull(response);
        assertEquals(4, response.getStars());
        assertEquals(5, response.getTimelinessScore());
        assertEquals(3, response.getCommunicationScore());
        assertEquals(4, response.getAccuracyScore());
        verify(ratingRepository).save(argThat(rating ->
            rating.getTimelinessScore() == 5 &&
            rating.getCommunicationScore() == 3 &&
            rating.getAccuracyScore() == 4
        ));
    }
}
