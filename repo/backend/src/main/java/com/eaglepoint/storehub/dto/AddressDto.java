package com.eaglepoint.storehub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddressDto {
    private Long id;

    @NotBlank @Size(max = 100)
    private String label;

    @NotBlank
    private String street;

    @NotBlank @Size(max = 100)
    private String city;

    @NotBlank @Size(max = 50)
    private String state;

    @NotBlank @Size(max = 10)
    private String zipCode;

    private boolean isDefault;
}
