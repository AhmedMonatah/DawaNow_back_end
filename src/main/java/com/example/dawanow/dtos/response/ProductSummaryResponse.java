package com.example.dawanow.dtos.response;

import java.math.BigDecimal;

public record ProductSummaryResponse(
        Long id,
        String name,
        String productName,
        String strength,
        String packSize,
        String form,
        BigDecimal price,
        String scientificName,
        String company,
        String route,
        String description,
        String imageUrl
) {
}
