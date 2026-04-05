package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.entity.Rating;
import com.eaglepoint.storehub.enums.AppealStatus;
import com.eaglepoint.storehub.repository.RatingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Scheduled service that processes expired appeals.
 * Pending appeals past their deadline are moved to EXPIRED status.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppealExpiryService {

    private final RatingRepository ratingRepository;
    private final AuditService auditService;

    @Scheduled(fixedRate = 3600_000) // every hour
    @Transactional
    public void processExpiredAppeals() {
        List<Rating> expired = ratingRepository.findExpiredAppeals();

        for (Rating rating : expired) {
            rating.setAppealStatus(AppealStatus.EXPIRED);
            ratingRepository.save(rating);

            auditService.logSystemAction("APPEAL_EXPIRED", "Rating", rating.getId(),
                    "Appeal expired past deadline for rating " + rating.getId());

            log.info("Appeal expired: ratingId={}, deadline={}", rating.getId(), rating.getAppealDeadline());
        }

        if (!expired.isEmpty()) {
            log.info("Appeal expiry check: {} appeals expired", expired.size());
        }
    }
}
