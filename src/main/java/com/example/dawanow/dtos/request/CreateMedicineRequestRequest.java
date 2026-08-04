package com.example.dawanow.dtos.request;

import com.example.dawanow.entity.PaymentMethod;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record CreateMedicineRequestRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double deliveryLatitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double deliveryLongitude,
        String deliveryAddress,
        String notes,
        @NotNull PaymentMethod paymentMethod
) {
}
