package com.eaglepoint.storehub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "delivery_zones")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeliveryZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private Organization site;

    @Column(nullable = false, length = 10)
    private String zipCode;

    @Column(nullable = false)
    private Double distanceMiles;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryFee;

    @Column(nullable = false)
    private boolean active;
}
