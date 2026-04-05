package com.eaglepoint.storehub.repository;

import com.eaglepoint.storehub.entity.AnalyticsEvent;
import com.eaglepoint.storehub.enums.EventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {

    long countBySiteIdAndEventTypeAndCreatedAtBetween(
            Long siteId, EventType eventType, Instant start, Instant end);

    @Query(value = """
        SELECT event_type, COUNT(*) AS cnt, COUNT(DISTINCT user_id) AS uniq
        FROM analytics_events
        WHERE site_id = :siteId AND created_at BETWEEN :start AND :end
        GROUP BY event_type
        """, nativeQuery = true)
    List<Object[]> getEventSummary(@Param("siteId") Long siteId,
                                    @Param("start") Instant start,
                                    @Param("end") Instant end);

    @Query(value = """
        SELECT DISTINCT user_id FROM analytics_events
        WHERE site_id = :siteId AND DATE(created_at) = DATE(:cohortDate) AND user_id IS NOT NULL
        """, nativeQuery = true)
    List<Long> findDistinctUserIdsBySiteAndDate(@Param("siteId") Long siteId, @Param("cohortDate") Instant cohortDate);

    @Query(value = """
        SELECT DISTINCT user_id FROM analytics_events
        WHERE site_id = :siteId AND DATE(created_at) = DATE(:targetDate) AND user_id IN :userIds
        """, nativeQuery = true)
    List<Long> findReturningUsers(@Param("siteId") Long siteId, @Param("targetDate") Instant targetDate, @Param("userIds") List<Long> userIds);
}
