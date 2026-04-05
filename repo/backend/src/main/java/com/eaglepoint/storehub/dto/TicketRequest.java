package com.eaglepoint.storehub.dto;

import com.eaglepoint.storehub.enums.TicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TicketRequest {
    @NotNull
    private Long orderId;

    @NotNull
    private TicketType type;

    @NotBlank
    private String description;

    private BigDecimal refundAmount;
}
