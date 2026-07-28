package com.example.dawanow.dtos.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreatePaymentIntentRequest(
        @NotNull(message = "Amount is required")
        @Min(value = 1, message = "Amount must be at least 1")
        Long amount,

        String currency,

        Long orderId
) {
}
