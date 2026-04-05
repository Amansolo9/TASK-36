package com.eaglepoint.storehub.entity;

import com.eaglepoint.storehub.enums.FulfillmentMode;
import com.eaglepoint.storehub.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private Organization site;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_staff_id")
    private User assignedStaff;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryFee;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Column(length = 10)
    private String deliveryZip;

    private Double deliveryDistanceMiles;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_mode", nullable = false)
    @Builder.Default
    private FulfillmentMode fulfillmentMode = FulfillmentMode.DELIVERY;

    @Column(name = "is_pickup")
    private boolean pickup;

    @Column(length = 6)
    private String pickupVerificationCode;

    @Column(name = "pickup_verified")
    private boolean pickupVerified;

    @Column(columnDefinition = "TEXT")
    private String courierNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_id")
    private User verifiedBy;

    private Instant verifiedAt;

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
