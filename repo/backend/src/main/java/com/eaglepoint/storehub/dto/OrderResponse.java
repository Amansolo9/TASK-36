package com.eaglepoint.storehub.dto;

import com.eaglepoint.storehub.enums.FulfillmentMode;
import com.eaglepoint.storehub.enums.OrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class OrderResponse {
    private Long id;
    private Long customerId;
    private Long siteId;
    private OrderStatus status;
    private FulfillmentMode fulfillmentMode;
    private BigDecimal subtotal;
    private BigDecimal deliveryFee;
    private BigDecimal total;
    private String deliveryZip;
    private Double deliveryDistanceMiles;
    private boolean pickup;
    private String pickupVerificationCode;
    private boolean pickupVerified;
    private String courierNotes;
    private Long verifiedById;
    private Instant verifiedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
