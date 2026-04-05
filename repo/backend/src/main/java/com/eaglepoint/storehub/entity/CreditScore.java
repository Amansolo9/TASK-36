package com.eaglepoint.storehub.entity;

import com.eaglepoint.storehub.converter.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "credit_scores")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreditScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "score", columnDefinition = "TEXT")
    @Builder.Default
    private String scoreEncrypted = "500";

    @Transient
    public int getScore() {
        return scoreEncrypted != null ? Integer.parseInt(scoreEncrypted) : 500;
    }

    public void setScore(int value) {
        this.scoreEncrypted = String.valueOf(value);
    }

    @Column(nullable = false)
    @Builder.Default
    private int ratingImpact = 0;

    @Column(nullable = false)
    @Builder.Default
    private int communityImpact = 0;

    @Column(nullable = false)
    @Builder.Default
    private int orderImpact = 0;

    @Column(nullable = false)
    @Builder.Default
    private int disputeImpact = 0;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
