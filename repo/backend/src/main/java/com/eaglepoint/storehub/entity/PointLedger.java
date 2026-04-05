package com.eaglepoint.storehub.entity;

import com.eaglepoint.storehub.enums.PointAction;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "point_ledger")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PointLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointAction action;

    @Column(nullable = false)
    private int points;

    private String description;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
