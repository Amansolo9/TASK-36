package com.eaglepoint.storehub.entity;

import com.eaglepoint.storehub.enums.ExperimentType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "experiments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Experiment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExperimentType type;

    @Column(nullable = false)
    private int variantCount;

    @Column(nullable = false)
    private boolean active;

    private String description;

    /**
     * Site scope for experiment tenancy. NULL means global (enterprise-level).
     * SITE_MANAGER can only mutate experiments with their own site_id.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id")
    private Organization site;

    @Column(nullable = false)
    @Builder.Default
    private int version = 1;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
