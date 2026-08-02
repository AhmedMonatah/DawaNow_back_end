package com.example.dawanow.dtos.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Two-phase DTO: RequestItemRepository.findByRequestIdIn populates everything
 * except `product` and `unitPrice` (via the 4-arg constructor below — JPQL
 * needs a constructor whose params match its select list exactly).
 * MedicineRequestService then fills in `product` and `unitPrice` via setters
 * once ProductSummaryResponses are batch-resolved from
 * ProductRepository.findAllLocalized. `productId` is kept even after that
 * happens — harmless duplication with product.getId(), and it's what lets the
 * "product was deleted" case be distinguished from "not resolved yet".
 * `requestId` exists only so MedicineRequestService can group batched lines
 * back to their request; it is not serialized.
 */
@Getter
@Setter
@NoArgsConstructor
public class MedicineRequestItemResponse {

    private Long id;

    @JsonIgnore
    private Long requestId;

    private Long productId;       // null if product was deleted

    private Long quantity;

    private Double unitPrice;     // null until MedicineRequestService resolves the product

    private ProductSummaryResponse product;  // null until MedicineRequestService resolves it; stays null if deleted

    // Called by RequestItemRepository's JPQL projection — product intentionally absent
    public MedicineRequestItemResponse(Long id, Long requestId, Long productId, Long quantity) {
        this.id = id;
        this.requestId = requestId;
        this.productId = productId;
        this.quantity = quantity;
    }
}
