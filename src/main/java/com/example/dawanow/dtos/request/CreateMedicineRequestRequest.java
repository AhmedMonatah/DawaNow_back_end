package com.example.dawanow.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateMedicineRequestRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double deliveryLatitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double deliveryLongitude,
        String deliveryAddress,
        @Valid @NotEmpty List<MedicineRequestItemRequest> items
) {
}
