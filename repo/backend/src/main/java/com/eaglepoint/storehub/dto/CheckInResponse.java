package com.eaglepoint.storehub.dto;

import com.eaglepoint.storehub.enums.CheckInStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
@Builder
public class CheckInResponse {
    private Long id;
    private Long userId;
    private Long siteId;
    private Instant timestamp;
    private Instant scheduledTime;
    private CheckInStatus status;
    private String message;
    private String locationDescription;
    private String deviceEvidenceToken; // masked hash of device fingerprint
    private String windowClassification; // EARLY, ON_TIME, LATE
    private boolean flaggedForReview;
}
