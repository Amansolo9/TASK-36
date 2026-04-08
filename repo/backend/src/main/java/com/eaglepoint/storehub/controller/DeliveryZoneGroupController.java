package com.eaglepoint.storehub.controller;

import com.eaglepoint.storehub.annotation.RequiresRecentAuth;
import com.eaglepoint.storehub.entity.DeliveryZoneGroup;
import com.eaglepoint.storehub.service.DeliveryZoneGroupService;
import com.eaglepoint.storehub.service.SiteAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/delivery-zone-groups")
@RequiredArgsConstructor
public class DeliveryZoneGroupController {

    private final DeliveryZoneGroupService groupService;
    private final SiteAuthorizationService siteAuth;

    @GetMapping("/site/{siteId}")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER')")
    public ResponseEntity<List<DeliveryZoneGroup>> getBySite(@PathVariable Long siteId) {
        siteAuth.requireSiteAccess(siteId);
        return ResponseEntity.ok(groupService.getBySite(siteId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER')")
    @RequiresRecentAuth
    public ResponseEntity<DeliveryZoneGroup> createGroup(
            @RequestParam Long siteId, @RequestParam String name) {
        siteAuth.requireSiteAccess(siteId);
        return ResponseEntity.ok(groupService.createGroup(siteId, name));
    }

    @PostMapping("/{groupId}/zips")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER')")
    @RequiresRecentAuth
    public ResponseEntity<DeliveryZoneGroup> addZipCode(
            @PathVariable Long groupId,
            @RequestParam String zipCode, @RequestParam double distanceMiles) {
        DeliveryZoneGroup group = groupService.getById(groupId);
        siteAuth.requireSiteAccess(group.getSite().getId());
        return ResponseEntity.ok(groupService.addZipCode(groupId, zipCode, distanceMiles));
    }

    @DeleteMapping("/{groupId}/zips/{zipCode}")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER')")
    @RequiresRecentAuth
    public ResponseEntity<DeliveryZoneGroup> removeZip(
            @PathVariable Long groupId, @PathVariable String zipCode) {
        DeliveryZoneGroup group = groupService.getById(groupId);
        siteAuth.requireSiteAccess(group.getSite().getId());
        return ResponseEntity.ok(groupService.removeZipCode(groupId, zipCode));
    }

    @PostMapping("/{groupId}/bands")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER')")
    @RequiresRecentAuth
    public ResponseEntity<DeliveryZoneGroup> addBand(
            @PathVariable Long groupId,
            @RequestParam double minMiles, @RequestParam double maxMiles,
            @RequestParam BigDecimal fee) {
        DeliveryZoneGroup group = groupService.getById(groupId);
        siteAuth.requireSiteAccess(group.getSite().getId());
        return ResponseEntity.ok(groupService.addDistanceBand(groupId, minMiles, maxMiles, fee));
    }

    @DeleteMapping("/{groupId}/bands/{bandId}")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER')")
    @RequiresRecentAuth
    public ResponseEntity<DeliveryZoneGroup> removeBand(
            @PathVariable Long groupId, @PathVariable Long bandId) {
        DeliveryZoneGroup group = groupService.getById(groupId);
        siteAuth.requireSiteAccess(group.getSite().getId());
        return ResponseEntity.ok(groupService.removeBand(groupId, bandId));
    }

    @PatchMapping("/{groupId}/deactivate")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER')")
    @RequiresRecentAuth
    public ResponseEntity<DeliveryZoneGroup> deactivate(@PathVariable Long groupId) {
        DeliveryZoneGroup group = groupService.getById(groupId);
        siteAuth.requireSiteAccess(group.getSite().getId());
        return ResponseEntity.ok(groupService.deactivate(groupId));
    }
}
