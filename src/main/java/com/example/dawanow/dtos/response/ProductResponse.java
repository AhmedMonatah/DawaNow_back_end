package com.example.dawanow.dtos.response;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String name,
        String scientificName,
        BigDecimal price,
        String imageUrl,
        Long categoryId,
        String categoryName,
        String company,
        String route
) {
}
