package com.example.dawanow.dtos.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Two-phase DTO: CartItemRepository.findByCartIdIn populates everything except
 * `product` (via the 5-arg constructor below — JPQL needs a constructor whose
 * params match its select list exactly). CartService then fills in `product`
 * via setter once ProductSummaryResponses are batch-resolved from
 * ProductRepository.findAllLocalized. `productId` is kept even after that
 * happens — harmless duplication with product.getId(), and it's what lets the
 * "product was deleted" case be distinguished from "not resolved yet".
 * `cartId` exists only so CartService can group batched lines back to their
 * cart; it is not serialized.
 */
@Getter
@Setter
@NoArgsConstructor
public class CartItemResponse {

    private Long id;

    @JsonIgnore
    private Long cartId;

    private Long productId;       // null if product was deleted

    private BigDecimal unitPrice;

    private Long quantity;

    private ProductSummaryResponse product;  // null until CartService resolves it; stays null if deleted

    // Called by CartItemRepository's JPQL projection — product intentionally absent
    public CartItemResponse(Long id, Long cartId, Long productId, BigDecimal unitPrice, Long quantity) {
        this.id = id;
        this.cartId = cartId;
        this.productId = productId;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public BigDecimal getSubtotal() {
        if (unitPrice == null || quantity == null) {
            return BigDecimal.ZERO;
        }
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
