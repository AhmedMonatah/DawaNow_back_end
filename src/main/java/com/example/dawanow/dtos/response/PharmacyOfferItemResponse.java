package com.example.dawanow.dtos.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Two-phase DTO: PharmacyOfferItemRepository.findByOfferIdIn populates
 * everything except `product` (via the 4-arg constructor below — JPQL needs a
 * constructor whose params match its select list exactly). PharmacyOfferService
 * then fills in `product` via setter once ProductSummaryResponses are
 * batch-resolved from ProductRepository.findAllLocalized. `productId` is kept
 * even after that happens — harmless duplication with product.getId(), and
 * it's what lets the "product was deleted" case be distinguished from "not
 * resolved yet". `offerId` exists only so PharmacyOfferService can group
 * batched lines back to their offer; it is not serialized.
 */
@Getter
@Setter
@NoArgsConstructor
public class PharmacyOfferItemResponse {

    private Long id;

    @JsonIgnore
    private Long offerId;

    private Long requestItemId;

    private Long productId;       // null if product was deleted

    private ProductSummaryResponse product;  // null until PharmacyOfferService resolves it; stays null if deleted

    // Called by PharmacyOfferItemRepository's JPQL projection — product intentionally absent
    public PharmacyOfferItemResponse(Long id, Long offerId, Long requestItemId, Long productId) {
        this.id = id;
        this.offerId = offerId;
        this.requestItemId = requestItemId;
        this.productId = productId;
    }
}
