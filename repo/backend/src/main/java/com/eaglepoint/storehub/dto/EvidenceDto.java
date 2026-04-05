package com.eaglepoint.storehub.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class EvidenceDto {
    private Long id;
    private String fileName;
    private String contentType;
    private long fileSize;
    private String sha256Hash;
    private Instant createdAt;
}
