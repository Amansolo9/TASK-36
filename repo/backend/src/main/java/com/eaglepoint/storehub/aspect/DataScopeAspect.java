package com.eaglepoint.storehub.aspect;

import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.enums.Role;
import com.eaglepoint.storehub.repository.OrganizationRepository;
import com.eaglepoint.storehub.repository.UserRepository;
import com.eaglepoint.storehub.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * Intercepts @DataScope-annotated methods and injects multi-dimensional scope context:
 * site IDs, team ID, device hash, and work-order ID.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class DataScopeAspect {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    @Around("@annotation(com.eaglepoint.storehub.annotation.DataScope)")
    public Object applyDataScope(ProceedingJoinPoint joinPoint) throws Throwable {
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

            // Device scope — derive from current request
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                String deviceHeader = req.getHeader("X-Device-Fingerprint");
                if (deviceHeader != null && !deviceHeader.isBlank()) {
                    DataScopeContext.setDeviceHash(deviceHeader);
                }
            }
        }

        try {
            return joinPoint.proceed();
        } finally {
            DataScopeContext.clear();
        }
    }
}
