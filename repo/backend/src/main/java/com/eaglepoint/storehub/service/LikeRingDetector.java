package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.entity.QuarantinedVote;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.enums.PointAction;
import com.eaglepoint.storehub.repository.QuarantinedVoteRepository;
import com.eaglepoint.storehub.repository.UserRepository;
import com.eaglepoint.storehub.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Background task that detects "Like Rings" — pairs of accounts that exchange
 * more than 20 votes within a 24-hour window — and quarantines them.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LikeRingDetector {

    private static final int VOTE_THRESHOLD = 20;
    private static final Duration DETECTION_WINDOW = Duration.ofHours(24);

    private final VoteRepository voteRepository;
    private final QuarantinedVoteRepository quarantinedVoteRepository;
    private final UserRepository userRepository;
    private final GamificationService gamificationService;

    @Scheduled(fixedRate = 600_000) // every 10 minutes
    @Transactional
    public void detectLikeRings() {
        Instant since = Instant.now().minus(DETECTION_WINDOW);
        List<Object[]> rings = voteRepository.findLikeRings(since, VOTE_THRESHOLD);

        for (Object[] row : rings) {
            Long voterId = ((BigInteger) row[0]).longValue();
            Long authorId = ((BigInteger) row[1]).longValue();
            int count = ((Number) row[2]).intValue();

            User voter = userRepository.findById(voterId).orElse(null);
            User author = userRepository.findById(authorId).orElse(null);
            if (voter == null || author == null) continue;

            // Idempotency: skip if unreviewed quarantine already exists for this pair
            if (quarantinedVoteRepository.existsByVoterIdAndPostAuthorIdAndReviewedFalse(voterId, authorId)) {
                log.debug("Like Ring already quarantined (unreviewed): voter={} -> author={}", voterId, authorId);
                continue;
            }

            log.warn("Like Ring detected: voter={} -> author={}, count={} in 24h",
                    voterId, authorId, count);

            QuarantinedVote qv = QuarantinedVote.builder()
                    .voter(voter)
                    .postAuthor(author)
                    .voteCount(count)
                    .reason("Like Ring: " + count + " votes from user " + voterId
                            + " to user " + authorId + " in 24h")
                    .build();

            quarantinedVoteRepository.save(qv);

            // Penalize both participants
            gamificationService.awardPoints(voterId, PointAction.QUARANTINED,
                    "Like Ring penalty (voter)");
            gamificationService.awardPoints(authorId, PointAction.QUARANTINED,
                    "Like Ring penalty (beneficiary)");
        }
    }
}
