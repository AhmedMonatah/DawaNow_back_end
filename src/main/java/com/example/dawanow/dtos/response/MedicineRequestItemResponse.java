package com.example.dawanow.dtos.response;

public record MedicineRequestItemResponse(
        Long id,
        Long productId,
        String imageUrl,
        String productName,
        String strength,
        String packSize,
        String form,
        Long quantity,
        Double unitPrice
) {
}
