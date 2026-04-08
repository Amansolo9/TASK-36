package com.eaglepoint.storehub.repository;

import com.eaglepoint.storehub.entity.CheckIn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    List<CheckIn> findByUserIdAndSiteIdOrderByTimestampDesc(Long userId, Long siteId);

    @Query("SELECT COUNT(c) FROM CheckIn c WHERE c.user.id = :userId AND c.timestamp > :since")
    long countByUserIdSince(@Param("userId") Long userId, @Param("since") Instant since);

    @Query("SELECT c FROM CheckIn c WHERE c.site.id = :siteId AND c.timestamp BETWEEN :start AND :end ORDER BY c.timestamp DESC")
    List<CheckIn> findBySiteAndTimeRange(@Param("siteId") Long siteId, @Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT DISTINCT c.deviceFingerprint FROM CheckIn c WHERE c.user.id = :userId")
    List<String> findDistinctDeviceFingerprintsByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(c) FROM CheckIn c WHERE c.user.id = :userId AND c.site.id = :siteId AND c.status = 'VALID' AND c.timestamp > :since")
    long countValidCheckInsSince(@Param("userId") Long userId, @Param("siteId") Long siteId, @Param("since") Instant since);

    /** Device-scoped query: find check-ins for a specific user+site+device combination */
    @Query("SELECT c FROM CheckIn c WHERE c.user.id = :userId AND c.site.id = :siteId AND c.deviceFingerprint = :deviceHash AND c.timestamp BETWEEN :start AND :end ORDER BY c.timestamp DESC")
    List<CheckIn> findByUserSiteDeviceAndTimeRange(@Param("userId") Long userId, @Param("siteId") Long siteId,
            @Param("deviceHash") String deviceHash, @Param("start") Instant start, @Param("end") Instant end);

    /** Work-order scoped: count check-ins for a specific shift assignment date */
    @Query("SELECT COUNT(c) FROM CheckIn c WHERE c.user.id = :userId AND c.site.id = :siteId AND c.deviceFingerprint = :deviceHash AND c.status = 'VALID' AND c.timestamp > :since")
    long countValidCheckInsByDevice(@Param("userId") Long userId, @Param("siteId") Long siteId,
            @Param("deviceHash") String deviceHash, @Param("since") Instant since);
}
