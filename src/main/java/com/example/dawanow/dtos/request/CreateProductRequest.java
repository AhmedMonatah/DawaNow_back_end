package com.example.dawanow.dtos.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank @Size(max = 500) String productName,
        @Size(max = 100) String strength,
        @Size(max = 100) String packSize,
        @NotBlank @Size(max = 100) String form,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal price,
        @NotBlank @Size(max = 1000) String scientificName,
        @NotBlank @Size(max = 255) String scientificCategory,
        @NotNull @Positive Long categoryId,
        @NotBlank @Size(max = 500) String company,
        @NotBlank @Size(max = 100) @Pattern(
                regexp = "EAR|EFF|EYE|INJECTION|MOUTH|ORAL\\.LIQUID|ORAL\\.SOLID|RECTAL|SPRAY|TOPICAL"
        ) String route,
        @NotBlank @Size(max = 2000) String description,
        @NotBlank @Size(max = 1000) @Pattern(regexp = "https://\\S+") String imageUrl,
        @NotBlank @Size(max = 500) String translatedName,
        @NotBlank @Size(max = 500) String translatedProductName,
        @Size(max = 100) String translatedStrength,
        @Size(max = 100) String translatedPackSize,
        @NotBlank @Size(max = 100) String translatedForm,
        @NotBlank @Size(max = 1000) String translatedScientificName,
        @NotBlank @Size(max = 255) String translatedScientificCategory,
        @NotBlank @Size(max = 255) String translatedConsumerCategory,
        @NotBlank @Size(max = 500) String translatedCompany,
        @NotBlank @Size(max = 100) String translatedRoute,
        @NotBlank @Size(max = 2000) String translatedDescription
) {
}
