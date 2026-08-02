package com.example.dawanow.dtos.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Two-phase DTO: OrderItemRepository.findByOrderIdIn populates everything
 * except `product` (via the 5-arg constructor below — JPQL needs a
 * constructor whose params match its select list exactly). OrderService then
 * fills in `product` via setter once ProductSummaryResponses are batch-resolved
 * from ProductRepository.findAllLocalized. `productId` is kept even after that
 * happens — harmless duplication with product.getId(), and it's what lets the
 * "product was deleted" case be distinguished from "not resolved yet".
 * `orderId` exists only so OrderService can group batched lines back to their
 * order; it is not serialized.
 */
@Getter
@Setter
@NoArgsConstructor
public class OrderItemResponse {

    private Long id;

    @JsonIgnore
    private Long orderId;

    private Long productId;       // null if product was deleted

    private Long quantity;

    private BigDecimal unitPrice;

    private ProductSummaryResponse product;  // null until OrderService resolves it; stays null if deleted

    // Called by OrderItemRepository's JPQL projection — product intentionally absent
    public OrderItemResponse(Long id, Long orderId, Long productId, Long quantity, BigDecimal unitPrice) {
        this.id = id;
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public BigDecimal getTotalPrice() {
        if (unitPrice == null || quantity == null) {
            return BigDecimal.ZERO;
        }
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
