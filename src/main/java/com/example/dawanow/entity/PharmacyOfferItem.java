package com.example.dawanow.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "pharmacy_offer_item",
        indexes = @Index(name = "idx_offer_item_request_product", columnList = "request_item_id, product_id"))
@Getter
@Setter
@NoArgsConstructor
public class PharmacyOfferItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id", nullable = false)
    private PharmacyOffer offer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_item_id", nullable = false)
    private RequestItem requestItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    private boolean alternative = false;

//    @Enumerated(EnumType.STRING)
//    @Column(nullable = false)
//    private OfferItemStatus status = OfferItemStatus.PENDING;
}
