package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.dto.PointsProfile;
import com.eaglepoint.storehub.entity.PointLedger;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.enums.CommunityLevel;
import com.eaglepoint.storehub.enums.PointAction;
import com.eaglepoint.storehub.enums.Role;
import com.eaglepoint.storehub.repository.PointLedgerRepository;
import com.eaglepoint.storehub.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GamificationServiceTest {

    @Mock
    private PointLedgerRepository pointLedgerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private IncentiveRuleService incentiveRuleService;

    @InjectMocks
    private GamificationService gamificationService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .passwordHash("encoded")
                .role(Role.CUSTOMER)
                .build();
    }

    @Test
    void awardPoints_postCreation_awards5Points() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(incentiveRuleService.getPoints("POST_CREATED")).thenReturn(5);
        when(pointLedgerRepository.save(any(PointLedger.class))).thenAnswer(invocation -> {
            PointLedger entry = invocation.getArgument(0);
            entry.setId(1L);
            return entry;
        });

        gamificationService.awardPoints(1L, PointAction.POST_CREATED, "Created a post");

        verify(pointLedgerRepository).save(argThat(entry ->
                entry.getAction() == PointAction.POST_CREATED
                        && entry.getPoints() == 5
                        && "Created a post".equals(entry.getDescription())));
    }

    @Test
    void awardPoints_upvote_awards1Point() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(incentiveRuleService.getPoints("UPVOTE_RECEIVED")).thenReturn(1);
        when(pointLedgerRepository.save(any(PointLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        gamificationService.awardPoints(1L, PointAction.UPVOTE_RECEIVED, "Received upvote");

        verify(pointLedgerRepository).save(argThat(entry ->
                entry.getAction() == PointAction.UPVOTE_RECEIVED
                        && entry.getPoints() == 1));
    }

    @Test
    void awardPoints_postRemoval_penaltyMinus10() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(incentiveRuleService.getPoints("POST_REMOVED")).thenReturn(-10);
        when(pointLedgerRepository.save(any(PointLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        gamificationService.awardPoints(1L, PointAction.POST_REMOVED, "Post removed by mod");

        verify(pointLedgerRepository).save(argThat(entry ->
                entry.getAction() == PointAction.POST_REMOVED
                        && entry.getPoints() == -10));
    }

    @Test
    void getProfile_0Points_newcomer() {
        when(pointLedgerRepository.getTotalPointsByUserId(1L)).thenReturn(0);

        PointsProfile profile = gamificationService.getProfile(1L);

        assertEquals(CommunityLevel.NEWCOMER, profile.getLevel());
        assertEquals(0, profile.getTotalPoints());
        assertEquals(100, profile.getPointsToNextLevel());
        assertEquals("Newcomer", profile.getBadge());
    }

    @Test
    void getProfile_100Points_contributor() {
        when(pointLedgerRepository.getTotalPointsByUserId(1L)).thenReturn(100);

        PointsProfile profile = gamificationService.getProfile(1L);

        assertEquals(CommunityLevel.CONTRIBUTOR, profile.getLevel());
        assertEquals(100, profile.getTotalPoints());
        assertEquals(200, profile.getPointsToNextLevel());
        assertEquals("Contributor", profile.getBadge());
    }

    @Test
    void getProfile_300Points_trusted() {
        when(pointLedgerRepository.getTotalPointsByUserId(1L)).thenReturn(300);

        PointsProfile profile = gamificationService.getProfile(1L);

        assertEquals(CommunityLevel.TRUSTED, profile.getLevel());
        assertEquals(300, profile.getTotalPoints());
        assertEquals(400, profile.getPointsToNextLevel());
        assertEquals("Trusted Member", profile.getBadge());
    }

    @Test
    void getProfile_700Points_champion() {
        when(pointLedgerRepository.getTotalPointsByUserId(1L)).thenReturn(700);

        PointsProfile profile = gamificationService.getProfile(1L);

        assertEquals(CommunityLevel.CHAMPION, profile.getLevel());
        assertEquals(700, profile.getTotalPoints());
        assertEquals(0, profile.getPointsToNextLevel());
        assertEquals("Community Champion", profile.getBadge());
    }

    @Test
    void getProfile_pointsToNextLevel_calculatedCorrectly() {
        // 50 points: NEWCOMER, needs 50 more to reach CONTRIBUTOR (100)
        when(pointLedgerRepository.getTotalPointsByUserId(1L)).thenReturn(50);

        PointsProfile profile = gamificationService.getProfile(1L);

        assertEquals(CommunityLevel.NEWCOMER, profile.getLevel());
        assertEquals(50, profile.getPointsToNextLevel());
    }
}
