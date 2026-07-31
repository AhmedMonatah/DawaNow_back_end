package com.example.dawanow.dtos.response;

import java.math.BigDecimal;

public record MostSoldProductResponse(
        Long productId,
        String productName,
        String imageUrl,
        long totalQuantitySold,
        BigDecimal totalRevenue
) {
}
