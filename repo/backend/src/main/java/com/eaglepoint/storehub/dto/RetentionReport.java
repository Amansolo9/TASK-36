package com.eaglepoint.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RetentionReport {
    private Long siteId;
    private String cohortDate;
    private int cohortSize;
    private double day1RetentionRate;
    private double day7RetentionRate;
    private double day30RetentionRate;
}
