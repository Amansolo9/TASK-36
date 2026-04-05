package com.eaglepoint.storehub.dto;

import com.eaglepoint.storehub.enums.OrgLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrganizationDto {
    private Long id;

    @NotBlank
    private String name;

    @NotNull
    private OrgLevel level;

    private Long parentId;
}
