package com.example.dawanow.dtos.response;

import java.math.BigDecimal;
import java.util.List;

public record ConfirmationResponse(
        Long requestId,
        List<OrderDraftResponse> offers,
        BigDecimal deliveryFees,
        BigDecimal totalPrice
) {
}
