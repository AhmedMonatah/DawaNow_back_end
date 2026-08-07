package com.example.dawanow.dtos.response;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Two-phase DTO for the medicine request result: the mappers populate
 * everything except `product`; MedicineRequestService fills in `product` via
 * setter once ProductSummaryResponses are batch-resolved from
 * ProductRepository.findAllLocalized. `productId` is kept even after that
 * happens — harmless duplication with product.getId(), and it's what lets the
 * "product was deleted" case be distinguished from "not resolved yet".
 * `alternatives` holds every alternative product found for an
 * ALTERNATIVE_FOUND item (populated by MedicineRequestService).
 */
@Getter
@Setter
@NoArgsConstructor
public class MedicineRequestResultItemResponse {

    private Long requestItemId;

    private Long productId;       // null if product was deleted

    private BigDecimal unitPrice;

    private Boolean alternative;

    private Boolean available;

    private ProductSummaryResponse product;  // null until MedicineRequestService resolves it; stays null if deleted

    private List<ProductSummaryResponse> alternatives = new ArrayList<>();
}
