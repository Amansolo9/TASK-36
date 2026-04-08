package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.config.AccessDeniedException;
import com.eaglepoint.storehub.enums.Role;
import com.eaglepoint.storehub.repository.OrganizationRepository;
import com.eaglepoint.storehub.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SiteAuthorizationService {

    private final OrganizationRepository organizationRepository;

    /**
     * Returns true if the current user can access data for the given site ID.
     * ENTERPRISE_ADMIN can access all sites.
     * SITE_MANAGER/TEAM_LEAD can access their site and child sites.
     * STAFF/CUSTOMER can only access their own site.
     */
    public boolean canAccessSite(Long siteId) {
        UserPrincipal principal = getCurrentPrincipal();
        if (principal == null) return false;

        Role role = Role.valueOf(principal.getRole());
        if (role == Role.ENTERPRISE_ADMIN) return true;

        // Null site: deny for non-admins (prevents accidental broad access)
        if (siteId == null) return false;

        Long userSiteId = principal.getSiteId();
        if (userSiteId == null) return false;
        if (userSiteId.equals(siteId)) return true;

        if (role == Role.SITE_MANAGER || role == Role.TEAM_LEAD) {
            List<Long> childSites = organizationRepository.findAllSiteIdsUnder(userSiteId);
            return childSites.contains(siteId);
        }

        return false;
    }

    /**
     * Throws AccessDeniedException if the current user cannot access the given site.
     */
    public void requireSiteAccess(Long siteId) {
        if (!canAccessSite(siteId)) {
            throw new AccessDeniedException("Access denied: resource belongs to a different site");
        }
    }

    /**
     * Checks if the current user is the given user ID, or has a managerial role for the given site.
     */
    public void requireOwnerOrSiteAccess(Long resourceOwnerId, Long siteId) {
        UserPrincipal principal = getCurrentPrincipal();
        if (principal == null) throw new AccessDeniedException("Not authenticated");
        if (principal.getId().equals(resourceOwnerId)) return;
        requireSiteAccess(siteId);
        Role role = Role.valueOf(principal.getRole());
        if (role != Role.ENTERPRISE_ADMIN && role != Role.SITE_MANAGER && role != Role.TEAM_LEAD && role != Role.STAFF) {
            throw new AccessDeniedException("Access denied");
        }
    }

    /**
     * Enforces that a device hash matches the expected binding for the operation.
     * Call when a device-scoped action requires the device to be the bound one.
     */
    public void requireDeviceMatch(String expectedHash, String actualHash) {
        if (expectedHash != null && !expectedHash.equals(actualHash)) {
            throw new AccessDeniedException("Device scope mismatch");
        }
    }

    /**
     * Enforces that a work-order/shift assignment exists and is active.
     * The shift assignment acts as the work-order binding for check-in operations.
     */
    public void requireWorkOrderScope(boolean hasActiveAssignment) {
        if (!hasActiveAssignment) {
            throw new AccessDeniedException("No active work-order/shift assignment for this operation");
        }
    }

    private UserPrincipal getCurrentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal p) return p;
        return null;
    }
}
