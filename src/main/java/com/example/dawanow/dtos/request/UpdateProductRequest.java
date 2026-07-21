package com.example.dawanow.dtos.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateProductRequest(
        @Size(max = 500) @Pattern(regexp = ".*\\S.*") String productName,
        @Size(max = 100) String strength,
        @Size(max = 100) String packSize,
        @Size(max = 100) @Pattern(regexp = ".*\\S.*") String form,
        @Positive @Digits(integer = 10, fraction = 2) BigDecimal price,
        @Size(max = 1000) @Pattern(regexp = ".*\\S.*") String scientificName,
        @Size(max = 255) @Pattern(regexp = ".*\\S.*") String scientificCategory,
        @Positive Long categoryId,
        @Size(max = 500) @Pattern(regexp = ".*\\S.*") String company,
        @Size(max = 100) @Pattern(
                regexp = "EAR|EFF|EYE|INJECTION|MOUTH|ORAL\\.LIQUID|ORAL\\.SOLID|RECTAL|SPRAY|TOPICAL"
        ) String route,
        @Size(max = 2000) @Pattern(regexp = ".*\\S.*") String description,
        @Size(max = 1000) @Pattern(regexp = "https://\\S+") String imageUrl,
        @Size(max = 500) @Pattern(regexp = ".*\\S.*") String translatedName,
        @Size(max = 500) @Pattern(regexp = ".*\\S.*") String translatedProductName,
        @Size(max = 100) String translatedStrength,
        @Size(max = 100) String translatedPackSize,
        @Size(max = 100) @Pattern(regexp = ".*\\S.*") String translatedForm,
        @Size(max = 1000) @Pattern(regexp = ".*\\S.*") String translatedScientificName,
        @Size(max = 255) @Pattern(regexp = ".*\\S.*") String translatedScientificCategory,
        @Size(max = 255) @Pattern(regexp = ".*\\S.*") String translatedConsumerCategory,
        @Size(max = 500) @Pattern(regexp = ".*\\S.*") String translatedCompany,
        @Size(max = 100) @Pattern(regexp = ".*\\S.*") String translatedRoute,
        @Size(max = 2000) @Pattern(regexp = ".*\\S.*") String translatedDescription
) {
}
