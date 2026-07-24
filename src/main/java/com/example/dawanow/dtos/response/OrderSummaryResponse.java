package com.example.dawanow.dtos.response;

import java.util.List;

public record OrderSummaryResponse(
        Long orderId,
        Long pharmacyId,
        String pharmacyName,
        List<Long> itemIds
) {
}
