package com.eaglepoint.storehub.repository;

import com.eaglepoint.storehub.entity.DeliveryZoneGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;

public interface DeliveryZoneGroupRepository extends JpaRepository<DeliveryZoneGroup, Long> {
    List<DeliveryZoneGroup> findBySiteIdAndActiveTrue(Long siteId);

    @Query("SELECT g FROM DeliveryZoneGroup g JOIN g.zips z WHERE g.site.id = :siteId AND z.zipCode = :zip AND g.active = true")
    Optional<DeliveryZoneGroup> findBySiteIdAndZipCode(@Param("siteId") Long siteId, @Param("zip") String zip);
}
