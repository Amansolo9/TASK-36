package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.annotation.Audited;
import com.eaglepoint.storehub.dto.RatingRequest;
import com.eaglepoint.storehub.dto.RatingResponse;
import com.eaglepoint.storehub.entity.Order;
import com.eaglepoint.storehub.entity.Rating;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.enums.AppealStatus;
import com.eaglepoint.storehub.enums.OrderStatus;
import com.eaglepoint.storehub.repository.OrderRepository;
import com.eaglepoint.storehub.repository.RatingRepository;
import com.eaglepoint.storehub.repository.UserRepository;
import com.eaglepoint.storehub.service.CreditScoreService;
import com.eaglepoint.storehub.service.SiteAuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RatingService {

    private static final Duration APPEAL_WINDOW = Duration.ofDays(7);

    private final RatingRepository ratingRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final CreditScoreService creditScoreService;
    private final SiteAuthorizationService siteAuth;

    @Audited(action = "CREATE", entityType = "Rating")
    @Transactional
    public RatingResponse submitRating(Long raterId, RatingRequest request) {
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        User rater = userRepository.findById(raterId)
                .orElseThrow(() -> new IllegalArgumentException("Rater not found"));
        User ratedUser = userRepository.findById(request.getRatedUserId())
                .orElseThrow(() -> new IllegalArgumentException("Rated user not found"));

        // Prevent duplicate ratings
        if (ratingRepository.findByOrderIdAndRaterId(request.getOrderId(), raterId).isPresent()) {
            throw new IllegalArgumentException("You have already rated this order");
        }

        // 1. Order must be completed
        OrderStatus orderStatus = order.getStatus();
        if (orderStatus != OrderStatus.DELIVERED && orderStatus != OrderStatus.PICKED_UP
                && orderStatus != OrderStatus.COURIER_DELIVERED) {
            throw new IllegalArgumentException("Can only rate completed orders");
        }

        // 2. Rater must be a real participant in the order
        boolean raterIsCustomer = order.getCustomer().getId().equals(raterId);
        boolean raterIsOrderStaff = (order.getAssignedStaff() != null && order.getAssignedStaff().getId().equals(raterId))
                || (order.getVerifiedBy() != null && order.getVerifiedBy().getId().equals(raterId));
        if (!raterIsCustomer && !raterIsOrderStaff) {
            throw new IllegalArgumentException("Rater is not a participant in this order");
        }

        // 3. Rated user must be the counterparty
        if (raterIsCustomer) {
            // Customer rating staff — verify rated user is actual order staff
            if (request.getRatedUserId().equals(raterId)) {
                throw new IllegalArgumentException("Cannot rate yourself");
            }
            if (request.getTargetType() != com.eaglepoint.storehub.enums.RatingTarget.STAFF) {
                throw new IllegalArgumentException("Customer can only rate staff");
            }
            // Rated staff must be linked to order (assigned, verifier)
            boolean ratedIsOrderStaff = (order.getAssignedStaff() != null && order.getAssignedStaff().getId().equals(request.getRatedUserId()))
                    || (order.getVerifiedBy() != null && order.getVerifiedBy().getId().equals(request.getRatedUserId()));
            if (!ratedIsOrderStaff) {
                throw new IllegalArgumentException("Rated user is not assigned to this order");
            }
        } else {
            // Staff rating customer — ratedUser must be the order's customer
            if (!request.getRatedUserId().equals(order.getCustomer().getId())) {
                throw new IllegalArgumentException("Staff can only rate the order's customer");
            }
            if (request.getTargetType() != com.eaglepoint.storehub.enums.RatingTarget.CUSTOMER) {
                throw new IllegalArgumentException("Staff can only rate as CUSTOMER target type");
            }
        }

        Rating rating = Rating.builder()
                .order(order)
                .rater(rater)
                .ratedUser(ratedUser)
                .targetType(request.getTargetType())
                .stars(request.getStars())
                .timelinessScore(request.getTimelinessScore())
                .communicationScore(request.getCommunicationScore())
                .accuracyScore(request.getAccuracyScore())
                .comment(request.getComment())
                .build();

        rating = ratingRepository.save(rating);

        // Update credit score from new rating
        Double avgStars = ratingRepository.findAverageStarsByRatedUserId(request.getRatedUserId());
        if (avgStars != null) {
            creditScoreService.updateFromRating(request.getRatedUserId(), avgStars.intValue());
        }

        log.info("Rating submitted: ratingId={}, raterId={}, ratedUserId={}, stars={}", rating.getId(), raterId, request.getRatedUserId(), request.getStars());
        return toResponse(rating);
    }

    @Audited(action = "APPEAL", entityType = "Rating")
    @Transactional
    public RatingResponse submitAppeal(Long ratingId, Long appealerId, String reason) {
        Rating rating = ratingRepository.findById(ratingId)
                .orElseThrow(() -> new IllegalArgumentException("Rating not found"));

        if (!rating.getRatedUser().getId().equals(appealerId)) {
            throw new IllegalArgumentException("Only the rated user can appeal");
        }
        if (rating.getAppealStatus() != null) {
            throw new IllegalArgumentException("Appeal already submitted");
        }

        // Check if within 7-day appeal window
        Instant deadline = rating.getCreatedAt().plus(APPEAL_WINDOW);
        if (Instant.now().isAfter(deadline)) {
            throw new IllegalArgumentException("Appeal window has expired (7 days)");
        }

        rating.setAppealStatus(AppealStatus.PENDING);
        rating.setAppealReason(reason);
        rating.setAppealDeadline(deadline);

        log.info("Appeal submitted: ratingId={}, appealerId={}", ratingId, appealerId);
        return toResponse(ratingRepository.save(rating));
    }

    @Audited(action = "RESOLVE_APPEAL", entityType = "Rating")
    @Transactional
    public RatingResponse resolveAppeal(Long ratingId, AppealStatus resolution, Long reviewerId, String notes) {
        Rating rating = ratingRepository.findById(ratingId)
                .orElseThrow(() -> new IllegalArgumentException("Rating not found"));

        // Site-scope enforcement on appeal resolution
        Long orderSiteId = rating.getOrder().getSite().getId();
        siteAuth.requireSiteAccess(orderSiteId);

        if (rating.getAppealStatus() != AppealStatus.PENDING
                && rating.getAppealStatus() != AppealStatus.IN_ARBITRATION) {
            throw new IllegalArgumentException("Appeal is not in a resolvable state");
        }

        rating.setAppealStatus(resolution);

        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new IllegalArgumentException("Reviewer not found"));
        rating.setArbitrationReviewer(reviewer);
        rating.setArbitrationResolvedAt(Instant.now());
        if (rating.getArbitrationStartedAt() == null) {
            rating.setArbitrationStartedAt(Instant.now());
        }
        rating.setArbitrationNotes(notes);

        rating = ratingRepository.save(rating);

        Double avgStars = ratingRepository.findAverageStarsByRatedUserId(rating.getRatedUser().getId());
        if (avgStars != null) {
            creditScoreService.updateFromRating(rating.getRatedUser().getId(), avgStars.intValue());
        }

        // Trigger dispute credit impact on appeal outcomes
        if (resolution == AppealStatus.OVERTURNED) {
            // Overturned appeal = dispute in favor of rated user, penalize rater
            long raterDisputes = ratingRepository.findByRatedUserIdOrderByCreatedAtDesc(rating.getRater().getId())
                    .stream().filter(r -> r.getAppealStatus() == AppealStatus.OVERTURNED).count();
            creditScoreService.updateFromDispute(rating.getRater().getId(), (int) raterDisputes);
        } else if (resolution == AppealStatus.UPHELD) {
            // Upheld appeal = dispute against rated user
            long ratedDisputes = ratingRepository.findByRatedUserIdOrderByCreatedAtDesc(rating.getRatedUser().getId())
                    .stream().filter(r -> r.getAppealStatus() == AppealStatus.UPHELD).count();
            creditScoreService.updateFromDispute(rating.getRatedUser().getId(), (int) ratedDisputes);
        }

        log.info("Appeal resolved: ratingId={}, resolution={}", ratingId, resolution);
        return toResponse(rating);
    }

    @Transactional(readOnly = true)
    public List<RatingResponse> getRatingsForUser(Long userId) {
        // Site-scope validation for rating reads
        User ratedUser = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        siteAuth.requireOwnerOrSiteAccess(userId, ratedUser.getSite() != null ? ratedUser.getSite().getId() : null);
        return ratingRepository.findByRatedUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Double getAverageRating(Long userId) {
        // Site-scope validation for rating reads
        User ratedUser = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        siteAuth.requireOwnerOrSiteAccess(userId, ratedUser.getSite() != null ? ratedUser.getSite().getId() : null);
        return ratingRepository.findAverageStarsByRatedUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<RatingResponse> getPendingAppeals() {
        return ratingRepository.findByAppealStatus(AppealStatus.PENDING).stream()
                .filter(r -> siteAuth.canAccessSite(r.getOrder().getSite().getId()))
                .map(this::toResponse).toList();
    }

    private RatingResponse toResponse(Rating r) {
        RatingResponse res = new RatingResponse();
        res.setId(r.getId());
        res.setOrderId(r.getOrder().getId());
        res.setRaterId(r.getRater().getId());
        res.setRatedUserId(r.getRatedUser().getId());
        res.setTargetType(r.getTargetType());
        res.setStars(r.getStars());
        res.setTimelinessScore(r.getTimelinessScore());
        res.setCommunicationScore(r.getCommunicationScore());
        res.setAccuracyScore(r.getAccuracyScore());
        res.setComment(r.getComment());
        res.setAppealStatus(r.getAppealStatus());
        res.setAppealDeadline(r.getAppealDeadline());
        res.setArbitrationReviewerId(r.getArbitrationReviewer() != null ? r.getArbitrationReviewer().getId() : null);
        res.setArbitrationStartedAt(r.getArbitrationStartedAt());
        res.setArbitrationResolvedAt(r.getArbitrationResolvedAt());
        res.setArbitrationNotes(r.getArbitrationNotes());
        res.setCreatedAt(r.getCreatedAt());
        return res;
    }
}
