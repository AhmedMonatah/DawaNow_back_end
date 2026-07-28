package com.example.dawanow.dtos.response;

import com.example.dawanow.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record OrderResponse(
        Long id,
        Long customerId,
        String customerName,
        String customerNotes,
        String deliveryAddress,
        String phoneNumber,
        String prescriptionUrl,
        Long pharmacyId,
        String pharmacyName,
        String pharmacyAddress,
        String pharmacyPhone,
        Long pharmacistId,
        String pharmacistName,
        Long offerId,
        BigDecimal subTotal,
        BigDecimal deliveryFee,
        BigDecimal total,
        Double deliveryLatitude,
        Double deliveryLongitude,
        LocalDate createdAt,
        List<OrderItemResponse> items
) {
}
