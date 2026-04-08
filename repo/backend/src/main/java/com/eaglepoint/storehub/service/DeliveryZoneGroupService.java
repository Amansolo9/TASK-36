package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.annotation.Audited;
import com.eaglepoint.storehub.entity.*;
import com.eaglepoint.storehub.repository.DeliveryZoneGroupRepository;
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
public class DeliveryZoneGroupService {

    private final DeliveryZoneGroupRepository groupRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional(readOnly = true)
    public List<DeliveryZoneGroup> getBySite(Long siteId) {
        return groupRepository.findBySiteIdAndActiveTrue(siteId);
    }

    @Transactional(readOnly = true)
    public DeliveryZoneGroup getById(Long id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Zone group not found"));
    }

    @Audited(action = "CREATE", entityType = "DeliveryZoneGroup")
    @Transactional
    public DeliveryZoneGroup createGroup(Long siteId, String name) {
        Organization site = organizationRepository.findById(siteId)
                .orElseThrow(() -> new IllegalArgumentException("Site not found"));
        DeliveryZoneGroup group = DeliveryZoneGroup.builder()
                .site(site).name(name).active(true).build();
        log.info("Delivery zone group created: siteId={}, name={}", siteId, name);
        return groupRepository.save(group);
    }

    @Audited(action = "ADD_ZIP", entityType = "DeliveryZoneGroup")
    @Transactional
    public DeliveryZoneGroup addZipCode(Long groupId, String zip, double distanceMiles) {
        if (zip == null || !zip.matches("^\\d{5}(-\\d{4})?$")) {
            throw new IllegalArgumentException("Invalid ZIP code format: " + zip);
        }
        if (distanceMiles < 0) {
            throw new IllegalArgumentException("Distance must be non-negative");
        }
        DeliveryZoneGroup group = getById(groupId);
        boolean exists = group.getZips().stream().anyMatch(z -> z.getZipCode().equals(zip));
        if (!exists) {
            group.getZips().add(DeliveryZoneZip.builder()
                    .group(group).zipCode(zip).distanceMiles(distanceMiles).build());
        }
        log.info("ZIP code added to group {}: {} ({}mi)", groupId, zip, distanceMiles);
        return groupRepository.save(group);
    }

    @Audited(action = "REMOVE_ZIP", entityType = "DeliveryZoneGroup")
    @Transactional
    public DeliveryZoneGroup removeZipCode(Long groupId, String zipCode) {
        DeliveryZoneGroup group = getById(groupId);
        group.getZips().removeIf(z -> z.getZipCode().equals(zipCode));
        return groupRepository.save(group);
    }

    @Audited(action = "ADD_BAND", entityType = "DeliveryZoneGroup")
    @Transactional
    public DeliveryZoneGroup addDistanceBand(Long groupId, double minMiles, double maxMiles, BigDecimal fee) {
        if (minMiles < 0 || maxMiles <= minMiles) {
            throw new IllegalArgumentException("Invalid band range: min must be >= 0 and max must be > min");
        }
        if (fee == null || fee.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Fee must be non-negative");
        }

        DeliveryZoneGroup group = getById(groupId);

        // Validate no overlapping bands
        for (DeliveryDistanceBand existing : group.getBands()) {
            if (minMiles < existing.getMaxMiles() && maxMiles > existing.getMinMiles()) {
                throw new IllegalArgumentException(
                    String.format("Band [%.1f-%.1f] overlaps with existing band [%.1f-%.1f]",
                            minMiles, maxMiles, existing.getMinMiles(), existing.getMaxMiles()));
            }
        }

        group.getBands().add(DeliveryDistanceBand.builder()
                .group(group).minMiles(minMiles).maxMiles(maxMiles).fee(fee).build());
        log.info("Distance band added to group {}: {}-{} mi = ${}", groupId, minMiles, maxMiles, fee);
        return groupRepository.save(group);
    }

    @Audited(action = "REMOVE_BAND", entityType = "DeliveryZoneGroup")
    @Transactional
    public DeliveryZoneGroup removeBand(Long groupId, Long bandId) {
        DeliveryZoneGroup group = getById(groupId);
        group.getBands().removeIf(b -> b.getId().equals(bandId));
        return groupRepository.save(group);
    }

    @Audited(action = "DEACTIVATE", entityType = "DeliveryZoneGroup")
    @Transactional
    public DeliveryZoneGroup deactivate(Long groupId) {
        DeliveryZoneGroup group = getById(groupId);
        group.setActive(false);
        return groupRepository.save(group);
    }

    /**
     * Resolves the delivery fee for a given site + ZIP.
     * Distance is sourced from the ZIP entry itself (not from external input).
     * Returns null if no matching group/band found.
     */
    @Transactional(readOnly = true)
    public FeeResult resolveDeliveryFee(Long siteId, String zipCode) {
        var groupOpt = groupRepository.findBySiteIdAndZipCode(siteId, zipCode);
        if (groupOpt.isEmpty()) return null;

        DeliveryZoneGroup group = groupOpt.get();

        // Get distance from the ZIP entry
        double distanceMiles = group.getZips().stream()
                .filter(z -> z.getZipCode().equals(zipCode))
                .findFirst()
                .map(DeliveryZoneZip::getDistanceMiles)
                .orElse(0.0);

        for (DeliveryDistanceBand band : group.getBands()) {
            if (distanceMiles >= band.getMinMiles() && distanceMiles < band.getMaxMiles()) {
                return new FeeResult(band.getFee(), distanceMiles);
            }
        }
        return null; // ZIP matched but no band covers this distance
    }

    /**
     * Result of fee resolution containing both the fee and the resolved distance.
     */
    public record FeeResult(BigDecimal fee, double distanceMiles) {}
}
