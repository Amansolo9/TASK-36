package com.eaglepoint.storehub.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReauthRequest {
    @NotBlank
    private String password;
}
