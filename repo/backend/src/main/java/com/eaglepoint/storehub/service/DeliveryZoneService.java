package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.annotation.Audited;
import com.eaglepoint.storehub.entity.DeliveryZone;
import com.eaglepoint.storehub.entity.Organization;
import com.eaglepoint.storehub.repository.DeliveryZoneRepository;
import com.eaglepoint.storehub.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryZoneService {

    private final DeliveryZoneRepository deliveryZoneRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional(readOnly = true)
    public List<DeliveryZone> getBySite(Long siteId) {
        return deliveryZoneRepository.findBySiteIdAndActiveTrue(siteId);
    }

    @Audited(action = "CREATE", entityType = "DeliveryZone")
    @Transactional
    public DeliveryZone create(Long siteId, String zipCode, double distanceMiles, BigDecimal deliveryFee) {
        validateZoneInput(zipCode, distanceMiles, deliveryFee);
        Organization site = organizationRepository.findById(siteId)
                .orElseThrow(() -> new IllegalArgumentException("Site not found"));
        DeliveryZone zone = DeliveryZone.builder()
                .site(site).zipCode(zipCode).distanceMiles(distanceMiles)
                .deliveryFee(deliveryFee).active(true).build();
        log.info("Delivery zone created: siteId={}, zip={}", siteId, zipCode);
        return deliveryZoneRepository.save(zone);
    }

    @Audited(action = "UPDATE", entityType = "DeliveryZone")
    @Transactional
    public DeliveryZone update(Long id, String zipCode, double distanceMiles, BigDecimal deliveryFee, boolean active) {
        validateZoneInput(zipCode, distanceMiles, deliveryFee);
        DeliveryZone zone = deliveryZoneRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Zone not found"));
        zone.setZipCode(zipCode);
        zone.setDistanceMiles(distanceMiles);
        zone.setDeliveryFee(deliveryFee);
        zone.setActive(active);
        log.info("Delivery zone updated: id={}, zip={}", id, zipCode);
        return deliveryZoneRepository.save(zone);
    }

    @Audited(action = "DELETE", entityType = "DeliveryZone")
    @Transactional
    public void delete(Long id) {
        DeliveryZone zone = deliveryZoneRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Zone not found"));
        log.info("Delivery zone deleted: id={}, siteId={}", id, zone.getSite().getId());
        deliveryZoneRepository.delete(zone);
    }

    @Transactional(readOnly = true)
    public DeliveryZone getById(Long id) {
        return deliveryZoneRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Zone not found"));
    }

    private void validateZoneInput(String zipCode, double distanceMiles, java.math.BigDecimal deliveryFee) {
        if (zipCode == null || !zipCode.matches("^\\d{5}(-\\d{4})?$")) {
            throw new IllegalArgumentException("Invalid ZIP code format (expected 5-digit or ZIP+4)");
        }
        if (distanceMiles < 0 || distanceMiles > 50) {
            throw new IllegalArgumentException("Distance must be between 0 and 50 miles");
        }
        if (deliveryFee == null || deliveryFee.compareTo(java.math.BigDecimal.ZERO) < 0
                || deliveryFee.compareTo(new java.math.BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Delivery fee must be between $0 and $100");
        }
    }
}
