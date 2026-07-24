package com.example.dawanow.dtos.response;

public record MedicineRequestItemResponse(
        Long id,
        Long productId,
        String imageUrl,
        String productName,
        Long quantity,
        Double unitPrice
) {
}
