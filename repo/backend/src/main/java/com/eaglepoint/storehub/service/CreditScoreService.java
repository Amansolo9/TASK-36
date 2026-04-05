package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.annotation.Audited;
import com.eaglepoint.storehub.dto.CreditScoreDto;
import com.eaglepoint.storehub.entity.CreditScore;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.repository.CreditScoreRepository;
import com.eaglepoint.storehub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreditScoreService {

    private final CreditScoreRepository creditScoreRepository;
    private final UserRepository userRepository;

    @Transactional
    public CreditScore getOrCreate(Long userId) {
        return creditScoreRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new IllegalArgumentException("User not found"));
                    CreditScore cs = CreditScore.builder()
                            .user(user)
                            .scoreEncrypted("500")
                            .ratingImpact(0)
                            .communityImpact(0)
                            .orderImpact(0)
                            .disputeImpact(0)
                            .build();
                    return creditScoreRepository.save(cs);
                });
    }

    @Audited(action = "UPDATE_FROM_RATING", entityType = "CreditScore")
    @Transactional
    public CreditScore updateFromRating(Long userId, int avgStars) {
        CreditScore cs = getOrCreate(userId);
        cs.setRatingImpact((avgStars - 3) * 40);
        cs.setScore(clampScore(cs));
        creditScoreRepository.save(cs);
        log.info("Credit score updated from rating: userId={}, ratingImpact={}, score={}", userId, cs.getRatingImpact(), cs.getScore());
        return cs;
    }

    @Audited(action = "UPDATE_FROM_COMMUNITY", entityType = "CreditScore")
    @Transactional
    public CreditScore updateFromCommunity(Long userId, int communityPoints) {
        CreditScore cs = getOrCreate(userId);
        cs.setCommunityImpact(communityPoints / 10);
        cs.setScore(clampScore(cs));
        creditScoreRepository.save(cs);
        log.info("Credit score updated from community: userId={}, communityImpact={}, score={}", userId, cs.getCommunityImpact(), cs.getScore());
        return cs;
    }

    @Audited(action = "UPDATE_FROM_ORDER", entityType = "CreditScore")
    @Transactional
    public CreditScore updateFromOrder(Long userId, int completedOrders) {
        CreditScore cs = getOrCreate(userId);
        cs.setOrderImpact(Math.min(completedOrders * 5, 100));
        cs.setScore(clampScore(cs));
        creditScoreRepository.save(cs);
        log.info("Credit score updated from orders: userId={}, orderImpact={}, score={}", userId, cs.getOrderImpact(), cs.getScore());
        return cs;
    }

    @Audited(action = "UPDATE_FROM_DISPUTE", entityType = "CreditScore")
    @Transactional
    public CreditScore updateFromDispute(Long userId, int disputes) {
        CreditScore cs = getOrCreate(userId);
        cs.setDisputeImpact(-disputes * 20);
        cs.setScore(clampScore(cs));
        creditScoreRepository.save(cs);
        log.info("Credit score updated from disputes: userId={}, disputeImpact={}, score={}", userId, cs.getDisputeImpact(), cs.getScore());
        return cs;
    }

    @Transactional(readOnly = true)
    public CreditScoreDto getScore(Long userId) {
        // Read-only: do not create on read
        CreditScore cs = creditScoreRepository.findByUserId(userId).orElse(null);
        if (cs == null) {
            // Return default values without persisting
            return new CreditScoreDto(userId, 500, 0, 0, 0, 0, "No credit score record yet. Score will be initialized on first transaction.");
        }
        String explanation = String.format(
                "Your score of %d is based on: Rating history (%+d), Community activity (%+d), Order completion (%+d), Disputes (%+d)",
                cs.getScore(), cs.getRatingImpact(), cs.getCommunityImpact(), cs.getOrderImpact(), cs.getDisputeImpact()
        );
        return new CreditScoreDto(
                userId,
                cs.getScore(),
                cs.getRatingImpact(),
                cs.getCommunityImpact(),
                cs.getOrderImpact(),
                cs.getDisputeImpact(),
                explanation
        );
    }

    private int clampScore(CreditScore cs) {
        int raw = 500 + cs.getRatingImpact() + cs.getCommunityImpact() + cs.getOrderImpact() + cs.getDisputeImpact();
        return Math.max(0, Math.min(1000, raw));
    }
}
