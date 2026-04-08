package com.eaglepoint.storehub.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "delivery_distance_bands")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeliveryDistanceBand {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private DeliveryZoneGroup group;

    @Column(nullable = false)
    private double minMiles;

    @Column(nullable = false)
    private double maxMiles;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal fee;
}
