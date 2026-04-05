package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.annotation.Audited;
import com.eaglepoint.storehub.dto.PointsProfile;
import com.eaglepoint.storehub.entity.PointLedger;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.enums.CommunityLevel;
import com.eaglepoint.storehub.enums.PointAction;
import com.eaglepoint.storehub.repository.PointLedgerRepository;
import com.eaglepoint.storehub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GamificationService {

    private final PointLedgerRepository pointLedgerRepository;
    private final UserRepository userRepository;
    private final IncentiveRuleService incentiveRuleService;

    @Audited(action = "AWARD_POINTS", entityType = "PointLedger")
    @Transactional
    public void awardPoints(Long userId, PointAction action, String description) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        int points = incentiveRuleService.getPoints(action.name());

        PointLedger entry = PointLedger.builder()
                .user(user)
                .action(action)
                .points(points)
                .description(description)
                .build();

        pointLedgerRepository.save(entry);
        log.info("Points awarded: userId={}, action={}, points={}", userId, action, points);
    }

    @Transactional(readOnly = true)
    public PointsProfile getProfile(Long userId) {
        int total = pointLedgerRepository.getTotalPointsByUserId(userId);
        CommunityLevel level = CommunityLevel.fromPoints(total);

        int pointsToNext;
        String badge;
        switch (level) {
            case NEWCOMER -> {
                pointsToNext = CommunityLevel.CONTRIBUTOR.getThreshold() - total;
                badge = "Newcomer";
            }
            case CONTRIBUTOR -> {
                pointsToNext = CommunityLevel.TRUSTED.getThreshold() - total;
                badge = "Contributor";
            }
            case TRUSTED -> {
                pointsToNext = CommunityLevel.CHAMPION.getThreshold() - total;
                badge = "Trusted Member";
            }
            case CHAMPION -> {
                pointsToNext = 0;
                badge = "Community Champion";
            }
            default -> {
                pointsToNext = 0;
                badge = "Newcomer";
            }
        }

        return new PointsProfile(userId, Math.max(total, 0), level, Math.max(pointsToNext, 0), badge);
    }
}
