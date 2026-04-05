package com.eaglepoint.storehub.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class AuditLogDto {
    private Long id;
    private Long userId;
    private String username;
    private String deviceFingerprint;
    private String action;
    private String entityType;
    private Long entityId;
    private String beforeState;
    private String afterState;
    private String ipAddress;
    private Instant createdAt;
}
