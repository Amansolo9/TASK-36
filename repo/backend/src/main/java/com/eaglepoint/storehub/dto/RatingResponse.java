package com.eaglepoint.storehub.dto;

import com.eaglepoint.storehub.enums.AppealStatus;
import com.eaglepoint.storehub.enums.RatingTarget;
import lombok.Data;

import java.time.Instant;

@Data
public class RatingResponse {
    private Long id;
    private Long orderId;
    private Long raterId;
    private Long ratedUserId;
    private RatingTarget targetType;
    private int stars;
    private int timelinessScore;
    private int communicationScore;
    private int accuracyScore;
    private String comment;
    private AppealStatus appealStatus;
    private Instant appealDeadline;
    private Long arbitrationReviewerId;
    private Instant arbitrationStartedAt;
    private Instant arbitrationResolvedAt;
    private String arbitrationNotes;
    private Instant createdAt;
}
