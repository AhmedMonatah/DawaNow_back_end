package com.example.dawanow.dtos.response;

public record MedicineRequestItemResponse(
        Long id,
        ProductSummaryResponse product,
        Long quantity,
        Double unitPrice
) {
}
