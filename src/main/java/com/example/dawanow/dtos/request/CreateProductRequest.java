package com.example.dawanow.dtos.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank @Size(max = 500) String name,
        @NotBlank @Size(max = 500) String translatedName,
        @NotBlank @Size(max = 1000) String scientificName,
        @NotBlank @Size(max = 1000) String translatedScientificName,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal price,
        @NotBlank @Size(max = 1000) @Pattern(regexp = "https://\\S+") String imageUrl,
        @NotNull @Positive Long categoryId,
        @NotBlank @Size(max = 255) String translatedCategoryName,
        @NotBlank @Size(max = 500) String company,
        @NotBlank @Size(max = 500) String translatedCompany,
        @NotBlank @Size(max = 100) @Pattern(
                regexp = "EAR|EFF|EYE|INJECTION|MOUTH|ORAL\\.LIQUID|ORAL\\.SOLID|RECTAL|SPRAY|TOPICAL"
        ) String route,
        @NotBlank @Size(max = 100) String translatedRoute
) {
}
