package com.example.dawanow.dtos.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateProductRequest(
        @Size(max = 500) @Pattern(regexp = ".*\\S.*") String name,
        @Size(max = 500) @Pattern(regexp = ".*\\S.*") String translatedName,
        @Size(max = 1000) @Pattern(regexp = ".*\\S.*") String scientificName,
        @Size(max = 1000) @Pattern(regexp = ".*\\S.*") String translatedScientificName,
        @Positive @Digits(integer = 10, fraction = 2) BigDecimal price,
        @Size(max = 1000) @Pattern(regexp = "https://\\S+") String imageUrl,
        @Positive Long categoryId,
        @Size(max = 255) @Pattern(regexp = ".*\\S.*") String translatedCategoryName,
        @Size(max = 500) @Pattern(regexp = ".*\\S.*") String company,
        @Size(max = 500) @Pattern(regexp = ".*\\S.*") String translatedCompany,
        @Size(max = 100) @Pattern(
                regexp = "EAR|EFF|EYE|INJECTION|MOUTH|ORAL\\.LIQUID|ORAL\\.SOLID|RECTAL|SPRAY|TOPICAL"
        ) String route,
        @Size(max = 100) @Pattern(regexp = ".*\\S.*") String translatedRoute
) {
}
