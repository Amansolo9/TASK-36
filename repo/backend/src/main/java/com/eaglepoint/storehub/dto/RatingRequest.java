package com.eaglepoint.storehub.dto;

import com.eaglepoint.storehub.enums.RatingTarget;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RatingRequest {
    @NotNull
    private Long orderId;

    @NotNull
    private Long ratedUserId;

    @NotNull
    private RatingTarget targetType;

    @NotNull @Min(1) @Max(5)
    private Integer stars;

    @Min(1) @Max(5)
    private Integer timelinessScore;

    @Min(1) @Max(5)
    private Integer communicationScore;

    @Min(1) @Max(5)
    private Integer accuracyScore;

    private String comment;
}
