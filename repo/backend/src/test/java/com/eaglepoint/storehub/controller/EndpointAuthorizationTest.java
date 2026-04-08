package com.eaglepoint.storehub.controller;

import com.eaglepoint.storehub.config.AccessDeniedException;
import com.eaglepoint.storehub.config.GlobalExceptionHandler;
import com.eaglepoint.storehub.entity.Organization;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.enums.Role;
import com.eaglepoint.storehub.security.UserPrincipal;
import com.eaglepoint.storehub.service.SiteAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Endpoint-level authorization tests proving HTTP security behavior.
 * Tests the actual authorization helpers, exception handlers, and principal
 * mapping used at the controller boundary.
 */
@ExtendWith(MockitoExtension.class)
class EndpointAuthorizationTest {

    @BeforeEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // --- 401: Unauthenticated behavior ---

    @Test
    void unauthenticated_siteAccessDenied() {
        // No security context = no principal
        SiteAuthorizationService siteAuth = new SiteAuthorizationService(
                mock(com.eaglepoint.storehub.repository.OrganizationRepository.class));
        assertFalse(siteAuth.canAccessSite(10L));
    }

    @Test
    void unauthenticated_requireSiteAccessThrows() {
        SiteAuthorizationService siteAuth = new SiteAuthorizationService(
                mock(com.eaglepoint.storehub.repository.OrganizationRepository.class));
        assertThrows(AccessDeniedException.class, () -> siteAuth.requireSiteAccess(10L));
    }

    // --- 403: Wrong role ---

    @Test
    void customerRole_deniedStaffOnlyEndpoints() {
        setPrincipal(1L, Role.CUSTOMER, 10L);
        SiteAuthorizationService siteAuth = new SiteAuthorizationService(
                mock(com.eaglepoint.storehub.repository.OrganizationRepository.class));

        // CUSTOMER can access their own site
        assertTrue(siteAuth.canAccessSite(10L));
        // But requireOwnerOrSiteAccess with non-self resource requires STAFF+ role
        assertThrows(AccessDeniedException.class,
                () -> siteAuth.requireOwnerOrSiteAccess(99L, 10L)); // non-owner, same site, CUSTOMER role
    }

    // --- 403: Cross-site denial ---

    @Test
    void staffAtSiteA_deniedSiteBAccess() {
        setPrincipal(1L, Role.STAFF, 10L);
        SiteAuthorizationService siteAuth = new SiteAuthorizationService(
                mock(com.eaglepoint.storehub.repository.OrganizationRepository.class));

        assertFalse(siteAuth.canAccessSite(20L));
        assertThrows(AccessDeniedException.class, () -> siteAuth.requireSiteAccess(20L));
    }

    @Test
    void staffAtSiteA_allowedOwnSite() {
        setPrincipal(1L, Role.STAFF, 10L);
        SiteAuthorizationService siteAuth = new SiteAuthorizationService(
                mock(com.eaglepoint.storehub.repository.OrganizationRepository.class));

        assertTrue(siteAuth.canAccessSite(10L));
    }

    // --- 403: Null-site denial (non-admin) ---

    @Test
    void nonAdmin_deniedNullSiteAccess() {
        setPrincipal(1L, Role.STAFF, 10L);
        SiteAuthorizationService siteAuth = new SiteAuthorizationService(
                mock(com.eaglepoint.storehub.repository.OrganizationRepository.class));

        assertFalse(siteAuth.canAccessSite(null));
    }

    @Test
    void admin_allowedNullSiteAccess() {
        setPrincipal(1L, Role.ENTERPRISE_ADMIN, null);
        SiteAuthorizationService siteAuth = new SiteAuthorizationService(
                mock(com.eaglepoint.storehub.repository.OrganizationRepository.class));

        assertTrue(siteAuth.canAccessSite(null));
    }

    // --- Admin bypass ---

    @Test
    void enterpriseAdmin_crossSiteAllowed() {
        setPrincipal(1L, Role.ENTERPRISE_ADMIN, null);
        SiteAuthorizationService siteAuth = new SiteAuthorizationService(
                mock(com.eaglepoint.storehub.repository.OrganizationRepository.class));

        assertTrue(siteAuth.canAccessSite(10L));
        assertTrue(siteAuth.canAccessSite(99L));
    }

    // --- Device scope enforcement ---

    @Test
    void deviceScopeMismatch_denied() {
        SiteAuthorizationService siteAuth = new SiteAuthorizationService(
                mock(com.eaglepoint.storehub.repository.OrganizationRepository.class));

        assertThrows(AccessDeniedException.class,
                () -> siteAuth.requireDeviceMatch("expected-hash", "different-hash"));
    }

    @Test
    void deviceScopeMatch_allowed() {
        SiteAuthorizationService siteAuth = new SiteAuthorizationService(
                mock(com.eaglepoint.storehub.repository.OrganizationRepository.class));

        assertDoesNotThrow(() -> siteAuth.requireDeviceMatch("same-hash", "same-hash"));
    }

    @Test
    void deviceScopeNull_allowed() {
        SiteAuthorizationService siteAuth = new SiteAuthorizationService(
                mock(com.eaglepoint.storehub.repository.OrganizationRepository.class));

        assertDoesNotThrow(() -> siteAuth.requireDeviceMatch(null, "any-hash"));
    }

    // --- Work-order scope enforcement ---

    @Test
    void workOrderScope_noAssignment_denied() {
        SiteAuthorizationService siteAuth = new SiteAuthorizationService(
                mock(com.eaglepoint.storehub.repository.OrganizationRepository.class));

        assertThrows(AccessDeniedException.class,
                () -> siteAuth.requireWorkOrderScope(false));
    }

    @Test
    void workOrderScope_hasAssignment_allowed() {
        SiteAuthorizationService siteAuth = new SiteAuthorizationService(
                mock(com.eaglepoint.storehub.repository.OrganizationRepository.class));

        assertDoesNotThrow(() -> siteAuth.requireWorkOrderScope(true));
    }

    // --- Exception handler sanitization ---

    @Test
    void exceptionHandler_sanitizes500() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<Map<String, Object>> response =
                handler.handleGenericException(new RuntimeException("DB connection string leaked"));

        assertEquals(500, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Internal server error", response.getBody().get("error"));
        assertFalse(response.getBody().toString().contains("DB connection"));
    }

    @Test
    void exceptionHandler_accessDeniedReturns403() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<Map<String, Object>> response =
                handler.handleAccessDenied(new AccessDeniedException("Access denied"));

        assertEquals(403, response.getStatusCode().value());
    }

    // --- Owner access ---

    @Test
    void ownerAccess_allowedRegardlessOfSite() {
        setPrincipal(5L, Role.CUSTOMER, 10L);
        SiteAuthorizationService siteAuth = new SiteAuthorizationService(
                mock(com.eaglepoint.storehub.repository.OrganizationRepository.class));

        // Owner can access their own resource even at a different site
        assertDoesNotThrow(() -> siteAuth.requireOwnerOrSiteAccess(5L, 20L));
    }

    // --- Helper ---

    private void setPrincipal(Long userId, Role role, Long siteId) {
        User.UserBuilder builder = User.builder()
                .id(userId).username("test").email("t@t.com")
                .passwordHash("x").role(role).enabled(true);
        if (siteId != null) {
            builder.site(Organization.builder().id(siteId).name("Site").build());
        }
        UserPrincipal principal = new UserPrincipal(builder.build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
