package com.eaglepoint.storehub.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class PostResponse {
    private Long id;
    private Long authorId;
    private String authorName;
    private String title;
    private String body;
    private String topic;
    private int upvotes;
    private int downvotes;
    private int netVotes;
    private int commentCount;
    private String currentUserVote; // "UPVOTE", "DOWNVOTE", or null
    private Instant createdAt;
}
