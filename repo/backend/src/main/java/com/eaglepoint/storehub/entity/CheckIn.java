package com.eaglepoint.storehub.entity;

import com.eaglepoint.storehub.converter.EncryptedStringConverter;
import com.eaglepoint.storehub.enums.CheckInStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "check_ins")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private Organization site;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    private Instant scheduledTime;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String deviceFingerprint;

    @Column(length = 500)
    private String locationDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckInStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (timestamp == null) timestamp = createdAt;
    }
}
