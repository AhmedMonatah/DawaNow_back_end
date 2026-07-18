package com.example.dawanow.dtos.response;

import java.math.BigDecimal;

public record CartItemResponse(Long id,
                               Long productId,
                               String productName,
                               String imageUrl,
                               BigDecimal unitPrice,
                               Long quantity,
                               BigDecimal subtotal) {
}
