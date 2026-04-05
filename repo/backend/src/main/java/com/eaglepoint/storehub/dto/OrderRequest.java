package com.eaglepoint.storehub.dto;

import com.eaglepoint.storehub.enums.FulfillmentMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderRequest {
    @NotNull
    private Long siteId;

    @NotNull @Positive
    private BigDecimal subtotal;

    @NotNull
    private FulfillmentMode fulfillmentMode;

    private String deliveryZip;

    private String courierNotes;

    // Backward compatibility
    public boolean isPickup() {
        return fulfillmentMode == FulfillmentMode.PICKUP;
    }
}
