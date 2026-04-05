package com.eaglepoint.storehub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "quarantined_votes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class QuarantinedVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voter_id", nullable = false)
    private User voter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_author_id", nullable = false)
    private User postAuthor;

    @Column(nullable = false)
    private int voteCount;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private boolean reviewed;

    @Column(nullable = false, updatable = false)
    private Instant detectedAt;

    @PrePersist
    protected void onCreate() {
        detectedAt = Instant.now();
    }
}
