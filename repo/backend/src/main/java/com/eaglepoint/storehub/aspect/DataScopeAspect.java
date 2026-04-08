package com.eaglepoint.storehub.aspect;

import com.eaglepoint.storehub.annotation.DataScope;
import com.eaglepoint.storehub.config.AccessDeniedException;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.enums.Role;
import com.eaglepoint.storehub.repository.OrganizationRepository;
import com.eaglepoint.storehub.repository.UserRepository;
import com.eaglepoint.storehub.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Intercepts @DataScope-annotated methods and injects multi-dimensional scope context:
 * site IDs, team ID, device hash, and work-order ID.
 *
 * When the annotation's requireDevice or requireWorkOrder flags are set, the aspect
 * enforces deny-by-default: the operation is rejected if the required dimension is
 * absent from the request context.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DataScopeAspect {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    @Around("@annotation(com.eaglepoint.storehub.annotation.DataScope)")
    public Object applyDataScope(ProceedingJoinPoint joinPoint) throws Throwable {
        // Read annotation attributes
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        DataScope annotation = method.getAnnotation(DataScope.class);
        boolean requireDevice = annotation != null && annotation.requireDevice();
        boolean requireWorkOrder = annotation != null && annotation.requireWorkOrder();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            Role role = Role.valueOf(principal.getRole());

            // Site scope
            List<Long> visibleSiteIds;
            if (role == Role.ENTERPRISE_ADMIN) {
                visibleSiteIds = null;
            } else if (role == Role.SITE_MANAGER || role == Role.TEAM_LEAD) {
                visibleSiteIds = principal.getSiteId() != null
                        ? organizationRepository.findAllSiteIdsUnder(principal.getSiteId())
                        : List.of();
            } else {
                visibleSiteIds = principal.getSiteId() != null
                        ? List.of(principal.getSiteId())
                        : List.of();
            }
            DataScopeContext.set(visibleSiteIds);

            // Team scope
            User user = userRepository.findById(principal.getId()).orElse(null);
            if (user != null && user.getTeam() != null) {
                DataScopeContext.setTeamId(user.getTeam().getId());
            }

            // Device scope — derive from current request header
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();

                String deviceHeader = req.getHeader("X-Device-Fingerprint");
                if (deviceHeader != null && !deviceHeader.isBlank()) {
                    DataScopeContext.setDeviceHash(deviceHeader);
                }

                // Work-order scope — from header or request parameter
                String workOrderHeader = req.getHeader("X-Work-Order-Id");
                if (workOrderHeader != null && !workOrderHeader.isBlank()) {
                    try {
                        DataScopeContext.setWorkOrderId(Long.parseLong(workOrderHeader.trim()));
                    } catch (NumberFormatException e) {
                        log.warn("Invalid X-Work-Order-Id header: {}", workOrderHeader);
                    }
                } else {
                    String workOrderParam = req.getParameter("workOrderId");
                    if (workOrderParam != null && !workOrderParam.isBlank()) {
                        try {
                            DataScopeContext.setWorkOrderId(Long.parseLong(workOrderParam.trim()));
                        } catch (NumberFormatException e) {
                            log.warn("Invalid workOrderId parameter: {}", workOrderParam);
                        }
                    }
                }
            }

            // Enforce required dimensions (deny-by-default).
            // Management roles (ENTERPRISE_ADMIN, SITE_MANAGER) are exempt — they
            // perform supervisory queries that should not be gated on device/work-order.
            // STAFF, TEAM_LEAD, and CUSTOMER must provide the required context.
            boolean managementRole = (role == Role.ENTERPRISE_ADMIN || role == Role.SITE_MANAGER);

            if (requireDevice && !managementRole && DataScopeContext.getDeviceHash() == null) {
                DataScopeContext.clear();
                throw new AccessDeniedException(
                        "Device scope required but no device fingerprint provided (X-Device-Fingerprint header)");
            }
            if (requireWorkOrder && !managementRole && DataScopeContext.getWorkOrderId() == null) {
                DataScopeContext.clear();
                throw new AccessDeniedException(
                        "Work-order scope required but no work-order ID provided (X-Work-Order-Id header)");
            }
        }

        try {
            return joinPoint.proceed();
        } finally {
            DataScopeContext.clear();
        }
    }
}
