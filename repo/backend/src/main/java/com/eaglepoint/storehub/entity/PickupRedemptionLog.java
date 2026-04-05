package com.eaglepoint.storehub.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "pickup_redemption_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PickupRedemptionLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verifier_id", nullable = false)
    private User verifier;

    @Column(nullable = false)
    private String outcome; // SUCCESS, DENIED_CUSTOMER_SELF, DENIED_WRONG_CODE, DENIED_ALREADY_VERIFIED, DENIED_NOT_PICKUP

    private String reason;

    @Column(nullable = false, updatable = false)
    private Instant attemptedAt;

    @PrePersist
    protected void onCreate() { attemptedAt = Instant.now(); }
}
