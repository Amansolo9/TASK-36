package com.eaglepoint.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class SiteMetrics {
    private Long siteId;
    private Map<String, Long> eventCounts;
    private Map<String, Long> uniqueUsers;
    private FunnelMetrics funnel;
    private SatisfactionMetrics satisfaction;
    private DiversityMetrics diversity;
    private String performanceStatus; // "On Time", "Late", "Flagged"

    @Data
    @AllArgsConstructor
    public static class FunnelMetrics {
        private long views;
        private long clicks;
        private long conversions;
        private double ctr;
        private double conversionRate;
    }

    @Data
    @AllArgsConstructor
    public static class SatisfactionMetrics {
        private long totalRatings;
        private long uniqueRaters;
        private long totalActiveUsers;
        private double satisfactionCoveragePct;
    }

    @Data
    @AllArgsConstructor
    public static class DiversityMetrics {
        private int distinctEventTypes;
        private long distinctActiveUsers;
        private double engagementDiversityIndex; // 0-1, higher = more diverse engagement spread
    }
}
