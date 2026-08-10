package com.example.dawanow.dtos.response;

public record OfferedItemResponse(
        Long itemId,
        Long productId,
        String productName
) {
}