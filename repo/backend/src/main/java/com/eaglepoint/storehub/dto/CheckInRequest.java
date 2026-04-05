package com.eaglepoint.storehub.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

@Data
public class CheckInRequest {
    @NotNull
    private Long siteId;

    /**
     * @deprecated Client-supplied scheduledTime is no longer used for validation.
     * The server derives the schedule from ShiftAssignment records.
     * Kept for backward compatibility and audit/reference purposes.
     */
    @Deprecated
    private Instant scheduledTime;

    private String deviceFingerprint;

    private String locationDescription;
}
