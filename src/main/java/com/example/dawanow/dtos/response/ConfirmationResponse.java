package com.example.dawanow.dtos.response;

import java.util.List;

public record ConfirmationResponse(
        Long requestId,
        List<OrderSummaryResponse> orders
) {
}
