package com.example.dawanow.dtos.request;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateProductRequest(
        String name,
        String arabicName,
        String scientificName,
        @PositiveOrZero BigDecimal price,
        @Size(max = 1000) String imageUrl,
        Long categoryId,
        String company,
        String route
) {
}
