package com.eaglepoint.storehub.dto;

import com.eaglepoint.storehub.enums.ExperimentType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ExperimentDto {
    private Long id;

    @NotBlank
    private String name;

    @NotNull
    private ExperimentType type;

    @Min(2)
    private int variantCount;

    private String description;
    private boolean active;
    private int version;
}
