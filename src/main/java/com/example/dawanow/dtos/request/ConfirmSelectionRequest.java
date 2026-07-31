package com.example.dawanow.dtos.request;

import com.example.dawanow.entity.PaymentMethod;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ConfirmSelectionRequest(
        @NotEmpty List<Long> selectedRequestItemIds,
        @NotNull(message = "paymentMethod is required")
        PaymentMethod paymentMethod
) {
}
