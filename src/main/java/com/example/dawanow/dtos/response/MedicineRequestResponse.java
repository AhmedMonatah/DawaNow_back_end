package com.example.dawanow.dtos.response;

import com.example.dawanow.entity.RequestStatus;
import java.time.LocalDateTime;
import java.util.List;

public record MedicineRequestResponse(
        Long id,
        Long customerId,
        Double deliveryLatitude,
        Double deliveryLongitude,
        String deliveryAddress,
        RequestStatus status,
        LocalDateTime createdAt,
        List<MedicineRequestItemResponse> items
) {
}
