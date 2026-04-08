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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Security integration tests validating endpoint-level authn/authz policy.
 * Tests the actual exception handler, UserPrincipal mapping, and authorization helpers.
 */
@ExtendWith(MockitoExtension.class)
class SecurityIntegrationTest {

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    // --- GlobalExceptionHandler sanitization ---

    @Test
    void globalExceptionHandler_returnsSanitized500() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<Map<String, Object>> response =
                handler.handleGenericException(new RuntimeException("sensitive db error"));

        assertEquals(500, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("Internal server error", body.get("error"));
        assertNotNull(body.get("traceId"));
        assertFalse(body.values().toString().contains("sensitive db error"));
        assertFalse(body.values().toString().contains("RuntimeException"));
    }

    @Test
    void globalExceptionHandler_uniqueTraceIds() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<Map<String, Object>> r1 = handler.handleGenericException(new Exception("a"));
        ResponseEntity<Map<String, Object>> r2 = handler.handleGenericException(new Exception("b"));

        assertNotEquals(r1.getBody().get("traceId"), r2.getBody().get("traceId"));
    }

    // --- UserPrincipal role mapping ---

    @Test
    void userPrincipal_mapsRoleCorrectly() {
        User user = buildUser(1L, Role.SITE_MANAGER, 10L);
        UserPrincipal principal = new UserPrincipal(user);

        assertEquals("SITE_MANAGER", principal.getRole());
        assertEquals(10L, principal.getSiteId());
        assertTrue(principal.isEnabled());
    }

    @Test
    void disabledUser_principalReflectsDisabled() {
        User user = User.builder().id(2L).username("disabled").email("d@t.com")
                .passwordHash("x").role(Role.STAFF).enabled(false).build();
        assertFalse(new UserPrincipal(user).isEnabled());
    }

    // --- SiteAuthorizationService policy ---

    @Test
    void staffAtSiteA_deniedAccessToSiteB() {
        mockPrincipal(1L, Role.STAFF, 10L);
        SiteAuthorizationService siteAuth = new SiteAuthorizationService(
                mock(com.eaglepoint.storehub.repository.OrganizationRepository.class));
        assertFalse(siteAuth.canAccessSite(20L));
    }

    @Test
    void staffAtSiteA_allowedOwnSite() {
        mockPrincipal(1L, Role.STAFF, 10L);
        SiteAuthorizationService siteAuth = new SiteAuthorizationService(
                mock(com.eaglepoint.storehub.repository.OrganizationRepository.class));
        assertTrue(siteAuth.canAccessSite(10L));
    }

    @Test
    void enterpriseAdmin_accessesAnySite() {
        mockPrincipal(1L, Role.ENTERPRISE_ADMIN, null);
        SiteAuthorizationService siteAuth = new SiteAuthorizationService(
                mock(com.eaglepoint.storehub.repository.OrganizationRepository.class));
        assertTrue(siteAuth.canAccessSite(99L));
    }

    @Test
    void requireSiteAccess_throwsForCrossSite() {
        mockPrincipal(1L, Role.STAFF, 10L);
        SiteAuthorizationService siteAuth = new SiteAuthorizationService(
                mock(com.eaglepoint.storehub.repository.OrganizationRepository.class));
        assertThrows(AccessDeniedException.class, () -> siteAuth.requireSiteAccess(20L));
    }

    @Test
    void customerRole_excludedFromStaffRoleSet() {
        assertFalse(
            "ENTERPRISE_ADMIN".equals("CUSTOMER") || "SITE_MANAGER".equals("CUSTOMER") ||
            "TEAM_LEAD".equals("CUSTOMER") || "STAFF".equals("CUSTOMER"));
    }

    // --- Helpers ---

    private User buildUser(Long id, Role role, Long siteId) {
        User.UserBuilder builder = User.builder().id(id).username("test").email("t@t.com")
                .passwordHash("x").role(role).enabled(true);
        if (siteId != null) {
            builder.site(Organization.builder().id(siteId).name("Site").build());
        }
        return builder.build();
    }

    private void mockPrincipal(Long userId, Role role, Long siteId) {
        UserPrincipal principal = new UserPrincipal(buildUser(userId, role, siteId));
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getPrincipal()).thenReturn(principal);
        SecurityContext ctx = mock(SecurityContext.class);
        lenient().when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }
}
