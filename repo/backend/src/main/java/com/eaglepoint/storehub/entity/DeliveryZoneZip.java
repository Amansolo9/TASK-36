package com.eaglepoint.storehub.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "delivery_zone_zips")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeliveryZoneZip {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private DeliveryZoneGroup group;

    @Column(nullable = false, length = 10)
    private String zipCode;

    @Column(nullable = false)
    private double distanceMiles;
}
