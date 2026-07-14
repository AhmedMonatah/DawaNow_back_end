package com.example.dawanow.dtos.response;

import com.example.dawanow.entity.OfferStatus;
import java.util.List;

public record PharmacyOfferResponse(
        Long id,
        Long requestId,
        Long pharmacyId,
        Long pharmacistId,
        OfferStatus status,
        Double distanceKm,
        List<PharmacyOfferItemResponse> items
) {
}
