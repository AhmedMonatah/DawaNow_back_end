package com.example.dawanow.dtos.response;

import java.util.List;

public record OrderDraftResponse(
        Long offerId,
        Long pharmacyId,
        String pharmacyName,
        Double latitude,
        Double longitude,
        List<OrderItemResponse> items
) {
}