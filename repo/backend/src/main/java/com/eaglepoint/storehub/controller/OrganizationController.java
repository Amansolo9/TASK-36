package com.eaglepoint.storehub.controller;

import com.eaglepoint.storehub.annotation.RequiresRecentAuth;
import com.eaglepoint.storehub.dto.OrganizationDto;
import com.eaglepoint.storehub.enums.OrgLevel;
import com.eaglepoint.storehub.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @PostMapping
    @PreAuthorize("hasRole('ENTERPRISE_ADMIN')")
    @RequiresRecentAuth
    public ResponseEntity<OrganizationDto> create(@Valid @RequestBody OrganizationDto dto) {
        return ResponseEntity.ok(organizationService.create(dto));
    }

    @GetMapping
    public ResponseEntity<List<OrganizationDto>> findAll() {
        return ResponseEntity.ok(organizationService.findAll());
    }

    @GetMapping("/level/{level}")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER','TEAM_LEAD','STAFF')")
    public ResponseEntity<List<OrganizationDto>> findByLevel(@PathVariable OrgLevel level) {
        return ResponseEntity.ok(organizationService.findByLevel(level));
    }

    @GetMapping("/{parentId}/children")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER','TEAM_LEAD','STAFF')")
    public ResponseEntity<List<OrganizationDto>> findChildren(@PathVariable Long parentId) {
        return ResponseEntity.ok(organizationService.findChildren(parentId));
    }
}
