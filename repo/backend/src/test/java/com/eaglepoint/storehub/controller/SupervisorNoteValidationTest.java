package com.eaglepoint.storehub.controller;

import com.eaglepoint.storehub.entity.FraudAlert;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.enums.Role;
import com.eaglepoint.storehub.repository.FraudAlertRepository;
import com.eaglepoint.storehub.repository.UserRepository;
import com.eaglepoint.storehub.security.UserPrincipal;
import com.eaglepoint.storehub.service.CheckInService;
import com.eaglepoint.storehub.service.SiteAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the real CheckInService.resolveFraudAlert supervisor note validation,
 * exercising actual service logic rather than duplicated helper methods.
 */
@ExtendWith(MockitoExtension.class)
class SupervisorNoteValidationTest {

    @Mock FraudAlertRepository fraudAlertRepository;
    @Mock UserRepository userRepository;
    @Mock SiteAuthorizationService siteAuth;

    // Other dependencies needed by CheckInService constructor
    @Mock com.eaglepoint.storehub.repository.CheckInRepository checkInRepository;
    @Mock com.eaglepoint.storehub.repository.OrganizationRepository organizationRepository;
    @Mock com.eaglepoint.storehub.repository.ShiftAssignmentRepository shiftAssignmentRepository;
    @Mock com.eaglepoint.storehub.repository.DeviceBindingRepository deviceBindingRepository;

    private CheckInService checkInService;

    @BeforeEach
    void setUp() {
        checkInService = new CheckInService(
                checkInRepository, fraudAlertRepository, userRepository,
                organizationRepository, shiftAssignmentRepository, deviceBindingRepository, siteAuth);

        // Set up security context for siteAuth
        User admin = User.builder().id(1L).username("admin").email("a@t.com")
                .passwordHash("x").role(Role.ENTERPRISE_ADMIN).enabled(true).build();
        UserPrincipal principal = new UserPrincipal(admin);
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getPrincipal()).thenReturn(principal);
        SecurityContext ctx = mock(SecurityContext.class);
        lenient().when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    @Test
    void resolveFraudAlert_shortNote_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> checkInService.resolveFraudAlert(1L, 1L, "too short"));
    }

    @Test
    void resolveFraudAlert_nullNote_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> checkInService.resolveFraudAlert(1L, 1L, null));
    }

    @Test
    void resolveFraudAlert_validNote_succeeds() {
        FraudAlert alert = FraudAlert.builder().id(1L)
                .user(User.builder().id(2L).build())
                .reason("TEST").details("test").build();
        User resolver = User.builder().id(1L).username("resolver").build();

        when(fraudAlertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(userRepository.findById(1L)).thenReturn(Optional.of(resolver));
        when(fraudAlertRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        FraudAlert result = checkInService.resolveFraudAlert(1L, 1L, "Confirmed false positive after review");

        assertTrue(result.isResolved());
        assertEquals("Confirmed false positive after review", result.getResolverNote());
        verify(fraudAlertRepository).save(any());
    }

    @Test
    void resolveFraudAlert_whitespaceOnly_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> checkInService.resolveFraudAlert(1L, 1L, "         "));
    }
}
