package com.eaglepoint.storehub.dto;

import com.eaglepoint.storehub.enums.TicketStatus;
import com.eaglepoint.storehub.enums.TicketType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
public class TicketResponse {
    private Long id;
    private Long orderId;
    private Long customerId;
    private String customerName;
    private Long assignedToId;
    private TicketType type;
    private TicketStatus status;
    private String description;
    private BigDecimal refundAmount;
    private boolean autoApproved;
    private boolean slaBreached;
    private Instant firstResponseDueAt;
    private Instant createdAt;
    private Instant updatedAt;
    private List<EvidenceDto> evidence;
}
