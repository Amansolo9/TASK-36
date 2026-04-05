package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.annotation.Audited;
import com.eaglepoint.storehub.dto.EventRequest;
import com.eaglepoint.storehub.dto.RetentionReport;
import com.eaglepoint.storehub.dto.SiteMetrics;
import com.eaglepoint.storehub.entity.AnalyticsEvent;
import com.eaglepoint.storehub.entity.Organization;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.repository.AnalyticsEventRepository;
import com.eaglepoint.storehub.repository.OrganizationRepository;
import com.eaglepoint.storehub.repository.SupportTicketRepository;
import com.eaglepoint.storehub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final AnalyticsEventRepository eventRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final SupportTicketRepository ticketRepository;
    private final SiteAuthorizationService siteAuth;

    @Audited(action = "LOG_EVENT", entityType = "AnalyticsEvent")
    @Transactional
    public void logEvent(Long userId, EventRequest request) {
        // Fix #9: Validate site access before persisting analytics event
        if (request.getSiteId() != null) {
            siteAuth.requireSiteAccess(request.getSiteId());
        }

        User user = userId != null ? userRepository.findById(userId).orElse(null) : null;
        Organization site = request.getSiteId() != null
                ? organizationRepository.findById(request.getSiteId()).orElse(null) : null;

        AnalyticsEvent event = AnalyticsEvent.builder()
                .user(user)
                .site(site)
                .eventType(request.getEventType())
                .target(request.getTarget())
                .metadata(request.getMetadata())
                .build();

        eventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public SiteMetrics getSiteMetrics(Long siteId, Instant start, Instant end) {
        List<Object[]> summary = eventRepository.getEventSummary(siteId, start, end);

        Map<String, Long> eventCounts = new LinkedHashMap<>();
        Map<String, Long> uniqueUsers = new LinkedHashMap<>();
        long views = 0, clicks = 0, conversions = 0;

        for (Object[] row : summary) {
            String type = (String) row[0];
            long count = ((Number) row[1]).longValue();
            long uniq = ((Number) row[2]).longValue();
            eventCounts.put(type, count);
            uniqueUsers.put(type, uniq);

            switch (type) {
                case "PAGE_VIEW" -> views = count;
                case "CLICK" -> clicks = count;
                case "CONVERSION" -> conversions = count;
            }
        }

        double ctr = views > 0 ? (double) clicks / views * 100 : 0;
        double convRate = clicks > 0 ? (double) conversions / clicks * 100 : 0;
        SiteMetrics.FunnelMetrics funnel = new SiteMetrics.FunnelMetrics(
                views, clicks, conversions, ctr, convRate);

        // Determine performance status based on SLA breaches
        long slaBreaches = ticketRepository.findOverdueSlaTickets(Instant.now()).stream()
                .filter(t -> t.getOrder().getSite().getId().equals(siteId))
                .count();

        String status;
        if (slaBreaches > 5) status = "Flagged";
        else if (slaBreaches > 0) status = "Late";
        else status = "On Time";

        // Satisfaction/diversity metrics
        long satRatings = eventCounts.getOrDefault("SATISFACTION_RATING", 0L);
        long satRaters = uniqueUsers.getOrDefault("SATISFACTION_RATING", 0L);
        long totalActive = uniqueUsers.values().stream().mapToLong(Long::longValue).max().orElse(0);
        double satCoverage = totalActive > 0 ? satRaters * 100.0 / totalActive : 0;
        SiteMetrics.SatisfactionMetrics satisfaction = new SiteMetrics.SatisfactionMetrics(
                satRatings, satRaters, totalActive, satCoverage);

        // Diversity metrics — measures engagement spread across event types and users
        int distinctTypes = eventCounts.size();
        long distinctUsers = uniqueUsers.values().stream().mapToLong(Long::longValue).max().orElse(0);
        // Shannon-inspired diversity: normalized entropy of event distribution
        double totalEvents = eventCounts.values().stream().mapToLong(Long::longValue).sum();
        double diversityIndex = 0;
        if (totalEvents > 0 && distinctTypes > 1) {
            for (long count : eventCounts.values()) {
                double p = count / totalEvents;
                if (p > 0) diversityIndex -= p * Math.log(p);
            }
            diversityIndex /= Math.log(distinctTypes); // normalize to 0-1
        }
        SiteMetrics.DiversityMetrics diversity = new SiteMetrics.DiversityMetrics(
                distinctTypes, distinctUsers, Math.round(diversityIndex * 100.0) / 100.0);

        return new SiteMetrics(siteId, eventCounts, uniqueUsers, funnel, satisfaction, diversity, status);
    }

    @Transactional(readOnly = true)
    public RetentionReport getRetentionCohorts(Long siteId, Instant cohortDate) {
        List<Long> cohortUsers = eventRepository.findDistinctUserIdsBySiteAndDate(siteId, cohortDate);
        if (cohortUsers.isEmpty()) {
            return new RetentionReport(siteId, cohortDate.toString(), 0, 0, 0, 0);
        }
        int size = cohortUsers.size();
        double d1 = countReturning(siteId, cohortDate.plus(Duration.ofDays(1)), cohortUsers) * 100.0 / size;
        double d7 = countReturning(siteId, cohortDate.plus(Duration.ofDays(7)), cohortUsers) * 100.0 / size;
        double d30 = countReturning(siteId, cohortDate.plus(Duration.ofDays(30)), cohortUsers) * 100.0 / size;
        return new RetentionReport(siteId, cohortDate.toString(), size, d1, d7, d30);
    }

    private int countReturning(Long siteId, Instant targetDate, List<Long> cohortUsers) {
        return eventRepository.findReturningUsers(siteId, targetDate, cohortUsers).size();
    }
}
