package com.example.dawanow.dtos.response;

import com.example.dawanow.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record OrderResponse(
        Long id,
        Long userId,
        Long pharmacyId,
        Long pharmacistId,
        Long offerId,
        BigDecimal totalPrice,
        Double deliveryLatitude,
        Double deliveryLongitude,
        OrderStatus status,
        LocalDate date,
        List<OrderItemResponse> items
) {
}
