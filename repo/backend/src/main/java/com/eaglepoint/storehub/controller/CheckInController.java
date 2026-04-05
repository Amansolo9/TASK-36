package com.eaglepoint.storehub.controller;

import com.eaglepoint.storehub.annotation.RequiresRecentAuth;
import com.eaglepoint.storehub.dto.CheckInRequest;
import com.eaglepoint.storehub.dto.CheckInResponse;
import com.eaglepoint.storehub.entity.FraudAlert;
import com.eaglepoint.storehub.security.UserPrincipal;
import com.eaglepoint.storehub.service.CheckInService;
import com.eaglepoint.storehub.service.SiteAuthorizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/checkins")
@RequiredArgsConstructor
public class CheckInController {

    private final CheckInService checkInService;
    private final SiteAuthorizationService siteAuth;

    @PostMapping
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER','TEAM_LEAD','STAFF')")
    public ResponseEntity<CheckInResponse> checkIn(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CheckInRequest request) {
        siteAuth.requireSiteAccess(request.getSiteId());
        return ResponseEntity.ok(checkInService.checkIn(principal.getId(), request));
    }

    @GetMapping("/site/{siteId}")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER','TEAM_LEAD')")
    public ResponseEntity<List<CheckInResponse>> getBySite(
            @PathVariable Long siteId,
            @RequestParam Instant start,
            @RequestParam Instant end) {
        siteAuth.requireSiteAccess(siteId);
        return ResponseEntity.ok(checkInService.getCheckInsBySite(siteId, start, end));
    }

    @GetMapping("/fraud-alerts")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER')")
    public ResponseEntity<List<FraudAlert>> getUnresolvedAlerts() {
        return ResponseEntity.ok(checkInService.getUnresolvedAlerts());
    }

    @PatchMapping("/fraud-alerts/{id}/resolve")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER')")
    @RequiresRecentAuth
    public ResponseEntity<Map<String, Object>> resolveFraudAlert(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String note) {
        FraudAlert alert = checkInService.resolveFraudAlert(id, principal.getId(), note);
        return ResponseEntity.ok(Map.of("id", id, "resolved", true));
    }
}
