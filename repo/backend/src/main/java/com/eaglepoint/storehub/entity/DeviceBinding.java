package com.eaglepoint.storehub.entity;

import com.eaglepoint.storehub.converter.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "device_bindings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeviceBinding {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String deviceHash;

    @Column(length = 100)
    private String deviceLabel;

    @Column(nullable = false, updatable = false)
    private Instant boundAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @PrePersist
    protected void onCreate() { boundAt = Instant.now(); }
}
