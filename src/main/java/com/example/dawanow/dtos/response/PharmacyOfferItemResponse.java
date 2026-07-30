package com.example.dawanow.dtos.response;

public record PharmacyOfferItemResponse(
        Long id,
        Long requestItemId,
        ProductSummaryResponse product
) {
}
