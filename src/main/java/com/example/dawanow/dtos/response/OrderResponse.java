package com.example.dawanow.dtos.response;

import com.example.dawanow.entity.OrderStatus;
import com.example.dawanow.entity.PaymentMethod;
import com.example.dawanow.entity.PaymentStatus;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
        OrderStatus status,
        @Mapping(target = "paymentMethod", source = "masterOrder.paymentMethod")
        PaymentMethod paymentMethod,
        @Mapping(target = "paymentStatus", source = "masterOrder.paymentStatus")
        PaymentStatus paymentStatus,
        @Mapping(target = "paidAt", source = "masterOrder.paidAt")
        LocalDateTime paidAt,
        List<OrderItemResponse> items
) {
}
