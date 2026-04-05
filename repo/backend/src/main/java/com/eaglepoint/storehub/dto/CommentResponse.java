package com.eaglepoint.storehub.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class CommentResponse {
    private Long id;
    private Long postId;
    private Long authorId;
    private String authorName;
    private String body;
    private Instant createdAt;
}
