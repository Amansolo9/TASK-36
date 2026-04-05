package com.eaglepoint.storehub.dto;

import com.eaglepoint.storehub.enums.EventType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EventRequest {
    @NotNull
    private EventType eventType;

    private Long siteId;
    private String target;
    private String metadata;
}
