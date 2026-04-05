package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.entity.QuarantinedVote;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.repository.QuarantinedVoteRepository;
import com.eaglepoint.storehub.repository.UserRepository;
import com.eaglepoint.storehub.repository.VoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LikeRingDetectorTest {

    @Mock private VoteRepository voteRepository;
    @Mock private QuarantinedVoteRepository quarantinedVoteRepository;
    @Mock private UserRepository userRepository;
    @Mock private GamificationService gamificationService;

    @InjectMocks private LikeRingDetector detector;

    private User voter;
    private User author;

    @BeforeEach
    void setUp() {
        voter = User.builder().id(1L).username("voter").build();
        author = User.builder().id(2L).username("author").build();
    }

    @Test
    void detectLikeRings_findsRing_quarantinesAndPenalizes() {
        // Simulate >20 votes from user 1 to user 2's posts in 24h
        Object[] ring = new Object[]{BigInteger.valueOf(1L), BigInteger.valueOf(2L), 25L};
        List<Object[]> rings = new java.util.ArrayList<>();
        rings.add(ring);
        when(voteRepository.findLikeRings(any(Instant.class), eq(20))).thenReturn(rings);
        when(userRepository.findById(1L)).thenReturn(Optional.of(voter));
        when(userRepository.findById(2L)).thenReturn(Optional.of(author));

        detector.detectLikeRings();

        verify(quarantinedVoteRepository).save(any(QuarantinedVote.class));
        verify(gamificationService, times(2)).awardPoints(anyLong(), any(), anyString());
    }

    @Test
    void detectLikeRings_noRings_doesNothing() {
        when(voteRepository.findLikeRings(any(Instant.class), eq(20))).thenReturn(List.of());

        detector.detectLikeRings();

        verify(quarantinedVoteRepository, never()).save(any());
        verify(gamificationService, never()).awardPoints(anyLong(), any(), anyString());
    }
}
