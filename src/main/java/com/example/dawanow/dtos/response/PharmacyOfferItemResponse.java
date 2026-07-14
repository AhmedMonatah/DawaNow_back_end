package com.example.dawanow.dtos.response;

import com.example.dawanow.entity.OfferItemStatus;

public record PharmacyOfferItemResponse(
        Long id,
        Long requestItemId,
        Long productId,
        Long quantity,
        OfferItemStatus status
) {
}
