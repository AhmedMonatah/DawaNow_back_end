package com.example.dawanow.dtos.response;

public record PaymentIntentResponse(
        String paymentIntentId,
        String clientSecret
) {
}
