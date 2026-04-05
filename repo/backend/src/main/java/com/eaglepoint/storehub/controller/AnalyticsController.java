package com.eaglepoint.storehub.controller;

import com.eaglepoint.storehub.annotation.RequiresRecentAuth;
import com.eaglepoint.storehub.dto.EventRequest;
import com.eaglepoint.storehub.dto.ExperimentDto;
import com.eaglepoint.storehub.dto.RetentionReport;
import com.eaglepoint.storehub.dto.SiteMetrics;
import com.eaglepoint.storehub.security.UserPrincipal;
import com.eaglepoint.storehub.service.AnalyticsService;
import com.eaglepoint.storehub.service.ExperimentService;
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
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final ExperimentService experimentService;
    private final SiteAuthorizationService siteAuth;

    // ───── Event Logging ─────

    @PostMapping("/events")
    public ResponseEntity<Void> logEvent(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody EventRequest request) {
        analyticsService.logEvent(principal != null ? principal.getId() : null, request);
        return ResponseEntity.ok().build();
    }

    // ───── Site Metrics ─────

    @GetMapping("/sites/{siteId}/metrics")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER')")
    public ResponseEntity<SiteMetrics> getSiteMetrics(
            @PathVariable Long siteId,
            @RequestParam Instant start,
            @RequestParam Instant end) {
        siteAuth.requireSiteAccess(siteId);
        return ResponseEntity.ok(analyticsService.getSiteMetrics(siteId, start, end));
    }

    // ───── Retention ─────

    @GetMapping("/sites/{siteId}/retention")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER')")
    public ResponseEntity<RetentionReport> getRetention(
            @PathVariable Long siteId, @RequestParam Instant cohortDate) {
        siteAuth.requireSiteAccess(siteId);
        return ResponseEntity.ok(analyticsService.getRetentionCohorts(siteId, cohortDate));
    }

    // ───── Experiments ─────

    @PostMapping("/experiments")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER')")
    @RequiresRecentAuth
    public ResponseEntity<ExperimentDto> createExperiment(@Valid @RequestBody ExperimentDto dto) {
        return ResponseEntity.ok(experimentService.createExperiment(dto));
    }

    @GetMapping("/experiments")
    public ResponseEntity<List<ExperimentDto>> getActiveExperiments() {
        return ResponseEntity.ok(experimentService.getActiveExperiments());
    }

    @GetMapping("/experiments/{name}/bucket")
    public ResponseEntity<Map<String, Object>> getBucket(
            @PathVariable String name,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(experimentService.getBucket(principal.getId(), name));
    }

    @PostMapping("/experiments/{name}/outcome")
    public ResponseEntity<Void> recordOutcome(
            @PathVariable String name,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam int variant,
            @RequestParam double reward) {
        experimentService.recordOutcome(name, principal != null ? principal.getId() : null, variant, reward);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/experiments/{id}")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER')")
    @RequiresRecentAuth
    public ResponseEntity<ExperimentDto> updateExperiment(
            @PathVariable Long id, @Valid @RequestBody ExperimentDto dto) {
        return ResponseEntity.ok(experimentService.updateExperiment(id, dto));
    }

    @PatchMapping("/experiments/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER')")
    @RequiresRecentAuth
    public ResponseEntity<ExperimentDto> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(experimentService.deactivate(id));
    }

    @PostMapping("/experiments/{id}/rollback")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER')")
    @RequiresRecentAuth
    public ResponseEntity<ExperimentDto> rollback(@PathVariable Long id) {
        return ResponseEntity.ok(experimentService.rollback(id));
    }
}
