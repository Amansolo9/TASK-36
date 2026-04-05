package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.dto.CheckInRequest;
import com.eaglepoint.storehub.dto.CheckInResponse;
import com.eaglepoint.storehub.entity.*;
import com.eaglepoint.storehub.enums.CheckInStatus;
import com.eaglepoint.storehub.enums.Role;
import com.eaglepoint.storehub.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckInServiceTest {

    @Mock
    private CheckInRepository checkInRepository;

    @Mock
    private FraudAlertRepository fraudAlertRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Mock
    private DeviceBindingRepository deviceBindingRepository;

    @Mock
    private SiteAuthorizationService siteAuth;

    @InjectMocks
    private CheckInService checkInService;

    private User testUser;
    private Organization testSite;

    @BeforeEach
    void setUp() {
        testSite = Organization.builder()
                .id(1L)
                .name("Test Site")
                .build();

        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .passwordHash("encoded")
                .role(Role.STAFF)
                .site(testSite)
                .build();
    }

    private CheckInRequest createRequest(Instant scheduledTime, String deviceFingerprint) {
        CheckInRequest request = new CheckInRequest();
        request.setSiteId(1L);
        request.setScheduledTime(scheduledTime);
        request.setDeviceFingerprint(deviceFingerprint);
        return request;
    }

    private ShiftAssignment buildShift(LocalTime shiftStart) {
        return ShiftAssignment.builder()
                .id(1L)
                .user(testUser)
                .site(testSite)
                .shiftDate(LocalDate.now())
                .shiftStart(shiftStart)
                .shiftEnd(shiftStart.plusHours(8))
                .active(true)
                .build();
    }

    @Test
    void checkIn_withinWindow_returnsValid() {
        Instant scheduled = Instant.now();
        CheckInRequest request = createRequest(scheduled, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(testSite));
        when(shiftAssignmentRepository.findActiveShift(eq(1L), eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(buildShift(LocalTime.now())));
        when(deviceBindingRepository.findByUserIdAndActiveTrue(1L)).thenReturn(Collections.emptyList());
        when(checkInRepository.countByUserIdSince(eq(1L), any(Instant.class))).thenReturn(0L);
        when(checkInRepository.countValidCheckInsSince(eq(1L), eq(1L), any(Instant.class))).thenReturn(0L);
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(invocation -> {
            CheckIn c = invocation.getArgument(0);
            c.setId(100L);
            return c;
        });

        CheckInResponse response = checkInService.checkIn(1L, request);

        assertNotNull(response);
        assertEquals(CheckInStatus.VALID, response.getStatus());
        assertEquals("Check-in successful.", response.getMessage());
        assertFalse(response.isFlaggedForReview());
        assertEquals("ON_TIME", response.getWindowClassification());
        verify(checkInRepository).save(any(CheckIn.class));
    }

    @Test
    void checkIn_tooEarly_returnsFlagged() {
        // Schedule the shift far in the future so "now" is well before the 15m window
        Instant scheduled = Instant.now().plus(30, ChronoUnit.MINUTES);
        CheckInRequest request = createRequest(scheduled, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(testSite));
        when(shiftAssignmentRepository.findActiveShift(eq(1L), eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(buildShift(LocalTime.now().plusMinutes(30))));
        when(deviceBindingRepository.findByUserIdAndActiveTrue(1L)).thenReturn(Collections.emptyList());
        when(checkInRepository.countByUserIdSince(eq(1L), any(Instant.class))).thenReturn(0L);
        when(checkInRepository.countValidCheckInsSince(eq(1L), eq(1L), any(Instant.class))).thenReturn(0L);
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(invocation -> {
            CheckIn c = invocation.getArgument(0);
            c.setId(101L);
            return c;
        });

        CheckInResponse response = checkInService.checkIn(1L, request);

        assertEquals(CheckInStatus.FLAGGED, response.getStatus());
        assertTrue(response.getMessage().contains("outside allowed window"));
        assertTrue(response.isFlaggedForReview());
        assertEquals("EARLY", response.getWindowClassification());
    }

    @Test
    void checkIn_tooLate_returnsFlagged() {
        // Schedule the shift far in the past so "now" is well after the 10m window
        Instant scheduled = Instant.now().minus(30, ChronoUnit.MINUTES);
        CheckInRequest request = createRequest(scheduled, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(testSite));
        when(shiftAssignmentRepository.findActiveShift(eq(1L), eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(buildShift(LocalTime.now().minusMinutes(30))));
        when(deviceBindingRepository.findByUserIdAndActiveTrue(1L)).thenReturn(Collections.emptyList());
        when(checkInRepository.countByUserIdSince(eq(1L), any(Instant.class))).thenReturn(0L);
        when(checkInRepository.countValidCheckInsSince(eq(1L), eq(1L), any(Instant.class))).thenReturn(0L);
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(invocation -> {
            CheckIn c = invocation.getArgument(0);
            c.setId(102L);
            return c;
        });

        CheckInResponse response = checkInService.checkIn(1L, request);

        assertEquals(CheckInStatus.FLAGGED, response.getStatus());
        assertTrue(response.getMessage().contains("outside allowed window"));
        assertTrue(response.isFlaggedForReview());
        assertEquals("LATE", response.getWindowClassification());
    }

    @Test
    void checkIn_excessiveAttempts_returnsFlagged() {
        Instant scheduled = Instant.now();
        CheckInRequest request = createRequest(scheduled, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(testSite));
        when(shiftAssignmentRepository.findActiveShift(eq(1L), eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(buildShift(LocalTime.now())));
        // Return 5 (which meets the >= 5 threshold)
        when(checkInRepository.countByUserIdSince(eq(1L), any(Instant.class))).thenReturn(5L);
        when(fraudAlertRepository.save(any(FraudAlert.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(invocation -> {
            CheckIn c = invocation.getArgument(0);
            c.setId(104L);
            return c;
        });

        CheckInResponse response = checkInService.checkIn(1L, request);

        assertEquals(CheckInStatus.FLAGGED, response.getStatus());
        assertTrue(response.getMessage().contains("flagged"));
        assertTrue(response.isFlaggedForReview());
        verify(fraudAlertRepository).save(any(FraudAlert.class));
        // Flagged check-in IS now persisted for supervisor review
        verify(checkInRepository).save(any(CheckIn.class));
    }

    @Test
    void checkIn_deviceMismatch_createsFraudAlert() {
        Instant scheduled = Instant.now();
        CheckInRequest request = createRequest(scheduled, "new-device-fp");

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(testSite));
        when(shiftAssignmentRepository.findActiveShift(eq(1L), eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(buildShift(LocalTime.now())));
        when(checkInRepository.countByUserIdSince(eq(1L), any(Instant.class))).thenReturn(0L);
        when(checkInRepository.countValidCheckInsSince(eq(1L), eq(1L), any(Instant.class))).thenReturn(0L);
        // User has a known device binding that does NOT match the new fingerprint
        DeviceBinding existingBinding = DeviceBinding.builder()
                .id(1L).user(testUser).deviceHash("old-device-fp").active(true).build();
        when(deviceBindingRepository.findByUserIdAndActiveTrue(1L)).thenReturn(List.of(existingBinding));
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(invocation -> {
            CheckIn c = invocation.getArgument(0);
            c.setId(103L);
            return c;
        });
        when(fraudAlertRepository.save(any(FraudAlert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CheckInResponse response = checkInService.checkIn(1L, request);

        // Check-in still proceeds (VALID), but a fraud alert is created for device mismatch
        assertEquals(CheckInStatus.VALID, response.getStatus());
        assertFalse(response.isFlaggedForReview());
        verify(fraudAlertRepository).save(argThat(alert ->
                "DEVICE_MISMATCH".equals(alert.getReason())));
    }
}
