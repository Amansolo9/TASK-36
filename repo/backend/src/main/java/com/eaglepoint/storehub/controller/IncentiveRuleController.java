package com.eaglepoint.storehub.controller;

import com.eaglepoint.storehub.annotation.RequiresRecentAuth;
import com.eaglepoint.storehub.entity.IncentiveRule;
import com.eaglepoint.storehub.service.IncentiveRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/incentive-rules")
@RequiredArgsConstructor
public class IncentiveRuleController {

    private final IncentiveRuleService incentiveRuleService;

    @GetMapping
    @PreAuthorize("hasRole('ENTERPRISE_ADMIN')")
    public ResponseEntity<List<IncentiveRule>> getAllRules() {
        return ResponseEntity.ok(incentiveRuleService.getAllRules());
    }

    @PutMapping("/{actionKey}")
    @PreAuthorize("hasRole('ENTERPRISE_ADMIN')")
    @RequiresRecentAuth
    public ResponseEntity<IncentiveRule> updateRule(
            @PathVariable String actionKey,
            @RequestParam int points) {
        return ResponseEntity.ok(incentiveRuleService.updateRule(actionKey, points));
    }

    @PatchMapping("/{actionKey}/toggle")
    @PreAuthorize("hasRole('ENTERPRISE_ADMIN')")
    @RequiresRecentAuth
    public ResponseEntity<IncentiveRule> toggleRule(
            @PathVariable String actionKey,
            @RequestParam boolean active) {
        return ResponseEntity.ok(incentiveRuleService.toggleRule(actionKey, active));
    }
}
