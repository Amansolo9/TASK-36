package com.eaglepoint.storehub.aspect;

import com.eaglepoint.storehub.annotation.DataScope;
import com.eaglepoint.storehub.config.AccessDeniedException;
import com.eaglepoint.storehub.entity.Organization;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.enums.Role;
import com.eaglepoint.storehub.repository.OrganizationRepository;
import com.eaglepoint.storehub.repository.UserRepository;
import com.eaglepoint.storehub.security.UserPrincipal;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Tests for multi-dimensional data-scope enforcement in the DataScopeAspect.
 * Proves that device and work-order scopes are propagated and enforced when required.
 */
@ExtendWith(MockitoExtension.class)
class DataScopeEnforcementTest {

    @Mock private OrganizationRepository orgRepo;
    @Mock private UserRepository userRepo;
    @Mock private ProceedingJoinPoint joinPoint;
    @Mock private MethodSignature methodSignature;

    private DataScopeAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new DataScopeAspect(orgRepo, userRepo);
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        DataScopeContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    // ═══════════════════════════════════════════════════════════
    //  Site scope basics
    // ═══════════════════════════════════════════════════════════

    @Test
    @DisplayName("STAFF at site 10 gets visible sites = [10]")
    void staffSiteScope() throws Throwable {
        setPrincipal(1L, Role.STAFF, 10L);
        setRequest(null, null);
        stubAnnotation(false, false);
        when(userRepo.findById(1L)).thenReturn(Optional.of(buildUser(1L, Role.STAFF, 10L)));
        when(joinPoint.proceed()).thenAnswer(inv -> {
            List<Long> sites = DataScopeContext.get();
            assertNotNull(sites);
            assertEquals(List.of(10L), sites);
            return null;
        });

        aspect.applyDataScope(joinPoint);
    }

    @Test
    @DisplayName("ENTERPRISE_ADMIN gets null visible sites (unrestricted)")
    void adminSiteScope() throws Throwable {
        setPrincipal(1L, Role.ENTERPRISE_ADMIN, null);
        setRequest(null, null);
        stubAnnotation(false, false);
        when(userRepo.findById(1L)).thenReturn(Optional.of(buildUser(1L, Role.ENTERPRISE_ADMIN, null)));
        when(joinPoint.proceed()).thenAnswer(inv -> {
            assertNull(DataScopeContext.get());
            return null;
        });

        aspect.applyDataScope(joinPoint);
    }

    // ═══════════════════════════════════════════════════════════
    //  Device scope: requireDevice = true
    // ═══════════════════════════════════════════════════════════

    @Test
    @DisplayName("requireDevice=true + device header present → succeeds")
    void requireDevice_withHeader_succeeds() throws Throwable {
        setPrincipal(1L, Role.STAFF, 10L);
        setRequest("device-abc", null);
        stubAnnotation(true, false);
        when(userRepo.findById(1L)).thenReturn(Optional.of(buildUser(1L, Role.STAFF, 10L)));
        when(joinPoint.proceed()).thenAnswer(inv -> {
            assertEquals("device-abc", DataScopeContext.getDeviceHash());
            return null;
        });

        aspect.applyDataScope(joinPoint);
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("requireDevice=true + no device header → AccessDeniedException")
    void requireDevice_noHeader_denied() throws Throwable {
        setPrincipal(1L, Role.STAFF, 10L);
        setRequest(null, null);
        stubAnnotation(true, false);
        when(userRepo.findById(1L)).thenReturn(Optional.of(buildUser(1L, Role.STAFF, 10L)));

        assertThrows(AccessDeniedException.class, () -> aspect.applyDataScope(joinPoint));
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("Same site but wrong device → device hash propagated, service can reject")
    void samesite_wrongDevice_propagated() throws Throwable {
        setPrincipal(1L, Role.STAFF, 10L);
        setRequest("wrong-device", null);
        stubAnnotation(true, false);
        when(userRepo.findById(1L)).thenReturn(Optional.of(buildUser(1L, Role.STAFF, 10L)));
        when(joinPoint.proceed()).thenAnswer(inv -> {
            // Device hash is in context — service can compare against binding
            assertEquals("wrong-device", DataScopeContext.getDeviceHash());
            return null;
        });

        aspect.applyDataScope(joinPoint);
    }

    // ═══════════════════════════════════════════════════════════
    //  Work-order scope: requireWorkOrder = true
    // ═══════════════════════════════════════════════════════════

    @Test
    @DisplayName("requireWorkOrder=true + work-order header present → succeeds")
    void requireWorkOrder_withHeader_succeeds() throws Throwable {
        setPrincipal(1L, Role.STAFF, 10L);
        setRequest(null, "42");
        stubAnnotation(false, true);
        when(userRepo.findById(1L)).thenReturn(Optional.of(buildUser(1L, Role.STAFF, 10L)));
        when(joinPoint.proceed()).thenAnswer(inv -> {
            assertEquals(42L, DataScopeContext.getWorkOrderId());
            return null;
        });

        aspect.applyDataScope(joinPoint);
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("requireWorkOrder=true + no work-order context → AccessDeniedException")
    void requireWorkOrder_noHeader_denied() throws Throwable {
        setPrincipal(1L, Role.STAFF, 10L);
        setRequest(null, null);
        stubAnnotation(false, true);
        when(userRepo.findById(1L)).thenReturn(Optional.of(buildUser(1L, Role.STAFF, 10L)));

        assertThrows(AccessDeniedException.class, () -> aspect.applyDataScope(joinPoint));
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("Same site + device but wrong work-order → work-order propagated, service can reject")
    void correctSiteDevice_wrongWorkOrder_propagated() throws Throwable {
        setPrincipal(1L, Role.STAFF, 10L);
        setRequest("device-abc", "999");  // wrong work-order
        stubAnnotation(true, true);
        when(userRepo.findById(1L)).thenReturn(Optional.of(buildUser(1L, Role.STAFF, 10L)));
        when(joinPoint.proceed()).thenAnswer(inv -> {
            assertEquals("device-abc", DataScopeContext.getDeviceHash());
            assertEquals(999L, DataScopeContext.getWorkOrderId());
            // Service layer would reject: work-order 999 not assigned to this user
            return null;
        });

        aspect.applyDataScope(joinPoint);
    }

    // ═══════════════════════════════════════════════════════════
    //  Full scope: correct site + device + work-order → success
    // ═══════════════════════════════════════════════════════════

    @Test
    @DisplayName("Correct full-scope (site + device + work-order) → succeeds with all context set")
    void fullScope_correct_succeeds() throws Throwable {
        setPrincipal(1L, Role.STAFF, 10L);
        setRequest("device-abc", "42");
        stubAnnotation(true, true);
        when(userRepo.findById(1L)).thenReturn(Optional.of(buildUser(1L, Role.STAFF, 10L)));
        when(joinPoint.proceed()).thenAnswer(inv -> {
            assertEquals(List.of(10L), DataScopeContext.get());
            assertEquals("device-abc", DataScopeContext.getDeviceHash());
            assertEquals(42L, DataScopeContext.getWorkOrderId());
            return "success";
        });

        Object result = aspect.applyDataScope(joinPoint);
        assertEquals("success", result);
    }

    // ═══════════════════════════════════════════════════════════
    //  Management role exemption
    // ═══════════════════════════════════════════════════════════

    @Test
    @DisplayName("ENTERPRISE_ADMIN exempt from requireDevice — no header needed")
    void adminExemptFromDeviceRequirement() throws Throwable {
        setPrincipal(1L, Role.ENTERPRISE_ADMIN, null);
        setRequest(null, null); // no device header
        stubAnnotation(true, false); // requireDevice=true
        when(userRepo.findById(1L)).thenReturn(Optional.of(buildUser(1L, Role.ENTERPRISE_ADMIN, null)));
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.applyDataScope(joinPoint);
        assertEquals("ok", result);
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("SITE_MANAGER exempt from requireWorkOrder — no header needed")
    void managerExemptFromWorkOrderRequirement() throws Throwable {
        setPrincipal(1L, Role.SITE_MANAGER, 10L);
        setRequest(null, null); // no work-order header
        stubAnnotation(false, true); // requireWorkOrder=true
        when(userRepo.findById(1L)).thenReturn(Optional.of(buildUser(1L, Role.SITE_MANAGER, 10L)));
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.applyDataScope(joinPoint);
        assertEquals("ok", result);
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("STAFF denied when requireDevice=true and no device header")
    void staffDeniedWithoutRequiredDevice() throws Throwable {
        setPrincipal(1L, Role.STAFF, 10L);
        setRequest(null, null); // no device header
        stubAnnotation(true, false); // requireDevice=true
        when(userRepo.findById(1L)).thenReturn(Optional.of(buildUser(1L, Role.STAFF, 10L)));

        assertThrows(AccessDeniedException.class, () -> aspect.applyDataScope(joinPoint));
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("TEAM_LEAD denied when requireWorkOrder=true and no work-order header")
    void teamLeadDeniedWithoutRequiredWorkOrder() throws Throwable {
        setPrincipal(1L, Role.TEAM_LEAD, 10L);
        setRequest("device-abc", null); // device present but no work-order
        stubAnnotation(false, true); // requireWorkOrder=true
        when(userRepo.findById(1L)).thenReturn(Optional.of(buildUser(1L, Role.TEAM_LEAD, 10L)));

        assertThrows(AccessDeniedException.class, () -> aspect.applyDataScope(joinPoint));
        verify(joinPoint, never()).proceed();
    }

    // ═══════════════════════════════════════════════════════════
    //  Dashboard/list reads: no device/work-order requirement
    // ═══════════════════════════════════════════════════════════

    @Test
    @DisplayName("STAFF can read with @DataScope (no requireDevice) without device header")
    void staff_dashboardRead_noDeviceRequired_succeeds() throws Throwable {
        setPrincipal(1L, Role.STAFF, 10L);
        setRequest(null, null); // no device or work-order headers
        stubAnnotation(false, false); // @DataScope without requirements
        when(userRepo.findById(1L)).thenReturn(Optional.of(buildUser(1L, Role.STAFF, 10L)));
        when(joinPoint.proceed()).thenReturn("dashboard-data");

        Object result = aspect.applyDataScope(joinPoint);
        assertEquals("dashboard-data", result);
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("TEAM_LEAD can read with @DataScope (no requireWorkOrder) without work-order header")
    void teamLead_dashboardRead_noWorkOrderRequired_succeeds() throws Throwable {
        setPrincipal(1L, Role.TEAM_LEAD, 10L);
        setRequest(null, null); // no headers
        stubAnnotation(false, false); // @DataScope without requirements
        when(userRepo.findById(1L)).thenReturn(Optional.of(buildUser(1L, Role.TEAM_LEAD, 10L)));
        when(orgRepo.findAllSiteIdsUnder(10L)).thenReturn(List.of(10L));
        when(joinPoint.proceed()).thenAnswer(inv -> {
            List<Long> sites = DataScopeContext.get();
            assertNotNull(sites);
            assertTrue(sites.contains(10L));
            return "task-list";
        });

        Object result = aspect.applyDataScope(joinPoint);
        assertEquals("task-list", result);
    }

    @Test
    @DisplayName("STAFF reading @DataScope with optional device header → device hash propagated for filtering")
    void staff_readWithOptionalDeviceHeader_propagated() throws Throwable {
        setPrincipal(1L, Role.STAFF, 10L);
        setRequest("device-abc", null); // device provided but not required
        stubAnnotation(false, false); // @DataScope without requirements
        when(userRepo.findById(1L)).thenReturn(Optional.of(buildUser(1L, Role.STAFF, 10L)));
        when(joinPoint.proceed()).thenAnswer(inv -> {
            // Device hash propagated for optional filtering even though not required
            assertEquals("device-abc", DataScopeContext.getDeviceHash());
            return null;
        });

        aspect.applyDataScope(joinPoint);
        verify(joinPoint).proceed();
    }

    // ═══════════════════════════════════════════════════════════
    //  Context cleanup
    // ═══════════════════════════════════════════════════════════

    @Test
    @DisplayName("Context is cleared after method execution even on success")
    void contextClearedAfterSuccess() throws Throwable {
        setPrincipal(1L, Role.STAFF, 10L);
        setRequest("device-abc", "42");
        stubAnnotation(false, false);
        when(userRepo.findById(1L)).thenReturn(Optional.of(buildUser(1L, Role.STAFF, 10L)));
        when(joinPoint.proceed()).thenReturn(null);

        aspect.applyDataScope(joinPoint);

        // Context should be cleared after the method returns
        assertNull(DataScopeContext.get());
        assertNull(DataScopeContext.getDeviceHash());
        assertNull(DataScopeContext.getWorkOrderId());
    }

    @Test
    @DisplayName("Context is cleared even when method throws")
    void contextClearedOnException() throws Throwable {
        setPrincipal(1L, Role.STAFF, 10L);
        setRequest("device-abc", "42");
        stubAnnotation(false, false);
        when(userRepo.findById(1L)).thenReturn(Optional.of(buildUser(1L, Role.STAFF, 10L)));
        when(joinPoint.proceed()).thenThrow(new RuntimeException("boom"));

        assertThrows(RuntimeException.class, () -> aspect.applyDataScope(joinPoint));

        assertNull(DataScopeContext.get());
        assertNull(DataScopeContext.getDeviceHash());
        assertNull(DataScopeContext.getWorkOrderId());
    }

    // ═══════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════

    private void setPrincipal(Long userId, Role role, Long siteId) {
        UserPrincipal principal = new UserPrincipal(buildUser(userId, role, siteId));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private User buildUser(Long id, Role role, Long siteId) {
        User.UserBuilder builder = User.builder()
                .id(id).username("test").email("t@t.com")
                .passwordHash("x").role(role).enabled(true);
        if (siteId != null) {
            builder.site(Organization.builder().id(siteId).name("Site").build());
        }
        return builder.build();
    }

    private void setRequest(String deviceFingerprint, String workOrderId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (deviceFingerprint != null) {
            request.addHeader("X-Device-Fingerprint", deviceFingerprint);
        }
        if (workOrderId != null) {
            request.addHeader("X-Work-Order-Id", workOrderId);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    /**
     * Stubs the joinPoint to return a MethodSignature with a @DataScope annotation
     * having the specified requireDevice and requireWorkOrder values.
     */
    private void stubAnnotation(boolean requireDevice, boolean requireWorkOrder) {
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        try {
            // Find the matching stub method
            String methodName;
            if (requireDevice && requireWorkOrder) {
                methodName = "stubBothRequired";
            } else if (requireDevice) {
                methodName = "stubDeviceRequired";
            } else if (requireWorkOrder) {
                methodName = "stubWorkOrderRequired";
            } else {
                methodName = "stubNoRequired";
            }
            Method method = StubMethods.class.getMethod(methodName);
            when(methodSignature.getMethod()).thenReturn(method);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    /** Stub methods with various @DataScope configurations for test annotation resolution */
    static class StubMethods {
        @DataScope
        public void stubNoRequired() {}

        @DataScope(requireDevice = true)
        public void stubDeviceRequired() {}

        @DataScope(requireWorkOrder = true)
        public void stubWorkOrderRequired() {}

        @DataScope(requireDevice = true, requireWorkOrder = true)
        public void stubBothRequired() {}
    }
}
