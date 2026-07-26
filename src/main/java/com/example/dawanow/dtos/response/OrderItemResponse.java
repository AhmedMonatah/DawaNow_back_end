package com.example.dawanow.dtos.response;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        Long productId,
        String productName,
        String imageUrl,
        Long quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice
) {
}
