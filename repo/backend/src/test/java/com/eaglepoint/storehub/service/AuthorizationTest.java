package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.config.AccessDeniedException;
import com.eaglepoint.storehub.entity.Order;
import com.eaglepoint.storehub.entity.Organization;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.enums.*;
import com.eaglepoint.storehub.repository.OrderRepository;
import com.eaglepoint.storehub.repository.OrganizationRepository;
import com.eaglepoint.storehub.security.UserPrincipal;
import com.eaglepoint.storehub.service.SiteAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorizationTest {

    @Mock OrganizationRepository organizationRepository;
    @InjectMocks SiteAuthorizationService siteAuth;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void staffAtSiteA_canAccessSiteA() {
        mockPrincipal(1L, "STAFF", 10L);
        assertTrue(siteAuth.canAccessSite(10L));
    }

    @Test
    void staffAtSiteA_cannotAccessSiteB() {
        mockPrincipal(1L, "STAFF", 10L);
        assertFalse(siteAuth.canAccessSite(20L));
    }

    @Test
    void enterpriseAdmin_canAccessAnySite() {
        mockPrincipal(1L, "ENTERPRISE_ADMIN", null);
        assertTrue(siteAuth.canAccessSite(10L));
        assertTrue(siteAuth.canAccessSite(20L));
    }

    @Test
    void siteManager_canAccessOwnSite() {
        mockPrincipal(1L, "SITE_MANAGER", 10L);
        when(organizationRepository.findAllSiteIdsUnder(10L)).thenReturn(List.of(10L, 11L));
        assertTrue(siteAuth.canAccessSite(10L));
    }

    @Test
    void siteManager_cannotAccessUnrelatedSite() {
        mockPrincipal(1L, "SITE_MANAGER", 10L);
        when(organizationRepository.findAllSiteIdsUnder(10L)).thenReturn(List.of(10L, 11L));
        assertFalse(siteAuth.canAccessSite(20L));
    }

    @Test
    void requireSiteAccess_throwsForCrossSite() {
        mockPrincipal(1L, "STAFF", 10L);
        assertThrows(AccessDeniedException.class, () -> siteAuth.requireSiteAccess(20L));
    }

    @Test
    void requireOwnerOrSiteAccess_allowsOwner() {
        mockPrincipal(5L, "CUSTOMER", 10L);
        assertDoesNotThrow(() -> siteAuth.requireOwnerOrSiteAccess(5L, 20L));
    }

    @Test
    void nullSite_handledGracefully() {
        mockPrincipal(1L, "ENTERPRISE_ADMIN", null);
        assertTrue(siteAuth.canAccessSite(null));
    }

    private void mockPrincipal(Long userId, String role, Long siteId) {
        User user = User.builder().id(userId).username("test").email("t@t.com")
                .passwordHash("x").role(Role.valueOf(role)).enabled(true).build();
        if (siteId != null) {
            user.setSite(Organization.builder().id(siteId).build());
        }
        UserPrincipal principal = new UserPrincipal(user);
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }
}
