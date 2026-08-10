package com.example.dawanow.dtos.response;

import com.example.dawanow.entity.FulfillmentMethod;
import com.example.dawanow.entity.OrderStatus;
import com.example.dawanow.entity.PaymentMethod;
import com.example.dawanow.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record MasterOrderResponse(
        Long id,
        Long requestId,
        List<OrderResponse> orderResponses,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        FulfillmentMethod fulfillmentMethod,
        BigDecimal deliveryFee,
        BigDecimal totalPrice,
        OrderStatus orderStatus,
        LocalDateTime paymentExpiresAt,
        LocalDateTime paidAt
) {
}