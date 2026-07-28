package com.example.dawanow.dtos.response;

public record PaymentIntentResponse(
        String paymentIntentId,
        String clientSecret,
        String publishableKey,
        long amount,
        String currency,
        String status
) {
}
