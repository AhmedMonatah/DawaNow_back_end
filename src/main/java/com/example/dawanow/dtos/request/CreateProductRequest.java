package com.example.dawanow.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank String name,
        String arabicName,
        String scientificName,
        @NotNull @PositiveOrZero BigDecimal price,
        @Size(max = 1000) String imageUrl,
        @NotNull Long categoryId,
        @NotBlank String company,
        String route
) {
}
