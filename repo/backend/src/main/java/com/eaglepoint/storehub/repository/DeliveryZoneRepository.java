package com.eaglepoint.storehub.repository;

import com.eaglepoint.storehub.entity.DeliveryZone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeliveryZoneRepository extends JpaRepository<DeliveryZone, Long> {

    List<DeliveryZone> findBySiteIdAndActiveTrue(Long siteId);

    Optional<DeliveryZone> findBySiteIdAndZipCode(Long siteId, String zipCode);
}
