package com.eaglepoint.storehub.dto;

import com.eaglepoint.storehub.enums.CommunityLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PointsProfile {
    private Long userId;
    private int totalPoints;
    private CommunityLevel level;
    private int pointsToNextLevel;
    private String badge;
}
