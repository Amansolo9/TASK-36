package com.eaglepoint.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreditScoreDto {
    private Long userId;
    private int score;
    private int ratingImpact;
    private int communityImpact;
    private int orderImpact;
    private int disputeImpact;
    private String explanation;
}
