package com.example.dawanow.dtos.response;

import java.math.BigDecimal;
import java.util.List;

public record MedicineRequestResultResponse(
        List<MedicineRequestResultItemResponse> medicineRequestResultItemList,
        BigDecimal totalPrice
) {
    // Custom no-arg constructor delegating to the canonical constructor
    public MedicineRequestResultResponse() {
        this(List.of(), BigDecimal.ZERO);
    }
}