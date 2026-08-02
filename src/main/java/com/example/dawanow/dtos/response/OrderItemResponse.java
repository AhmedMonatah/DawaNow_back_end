package com.example.dawanow.dtos.response;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        ProductSummaryResponse product,
        Long quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice
) {
}
