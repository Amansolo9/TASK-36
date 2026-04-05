package com.eaglepoint.storehub.controller;

import com.eaglepoint.storehub.annotation.RequiresRecentAuth;
import com.eaglepoint.storehub.entity.DeliveryZone;
import com.eaglepoint.storehub.service.DeliveryZoneService;
import com.eaglepoint.storehub.service.SiteAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/delivery-zones")
@RequiredArgsConstructor
public class DeliveryZoneController {

    private final DeliveryZoneService deliveryZoneService;
    private final SiteAuthorizationService siteAuth;

    @GetMapping("/site/{siteId}")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER')")
    public ResponseEntity<List<DeliveryZone>> getBySite(@PathVariable Long siteId) {
        siteAuth.requireSiteAccess(siteId);
        return ResponseEntity.ok(deliveryZoneService.getBySite(siteId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER')")
    @RequiresRecentAuth
    public ResponseEntity<DeliveryZone> create(
            @RequestParam Long siteId, @RequestParam String zipCode,
            @RequestParam double distanceMiles, @RequestParam BigDecimal deliveryFee) {
        siteAuth.requireSiteAccess(siteId);
        return ResponseEntity.ok(deliveryZoneService.create(siteId, zipCode, distanceMiles, deliveryFee));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER')")
    @RequiresRecentAuth
    public ResponseEntity<DeliveryZone> update(
            @PathVariable Long id, @RequestParam String zipCode,
            @RequestParam double distanceMiles, @RequestParam BigDecimal deliveryFee,
            @RequestParam boolean active) {
        DeliveryZone zone = deliveryZoneService.getById(id);
        siteAuth.requireSiteAccess(zone.getSite().getId());
        return ResponseEntity.ok(deliveryZoneService.update(id, zipCode, distanceMiles, deliveryFee, active));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ENTERPRISE_ADMIN')")
    @RequiresRecentAuth
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        DeliveryZone zone = deliveryZoneService.getById(id);
        siteAuth.requireSiteAccess(zone.getSite().getId());
        deliveryZoneService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
