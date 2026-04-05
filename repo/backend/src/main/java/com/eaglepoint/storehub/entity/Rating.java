package com.eaglepoint.storehub.entity;

import com.eaglepoint.storehub.enums.AppealStatus;
import com.eaglepoint.storehub.enums.RatingTarget;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ratings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rater_id", nullable = false)
    private User rater;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rated_user_id", nullable = false)
    private User ratedUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RatingTarget targetType;

    @Column(nullable = false)
    private int stars;

    @Column(nullable = false)
    private int timelinessScore;

    @Column(nullable = false)
    private int communicationScore;

    @Column(nullable = false)
    private int accuracyScore;

    private String comment;

    @Enumerated(EnumType.STRING)
    private AppealStatus appealStatus;

    private String appealReason;

    private Instant appealDeadline;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arbitration_reviewer_id")
    private User arbitrationReviewer;

    private Instant arbitrationStartedAt;

    private Instant arbitrationResolvedAt;

    @Column(columnDefinition = "TEXT")
    private String arbitrationNotes;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
