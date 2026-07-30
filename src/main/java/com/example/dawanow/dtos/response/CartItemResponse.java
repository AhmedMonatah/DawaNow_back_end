package com.example.dawanow.dtos.response;

import java.math.BigDecimal;

public record CartItemResponse(Long id,
                               ProductSummaryResponse product,
                               BigDecimal unitPrice,
                               Long quantity,
                               BigDecimal subtotal) {
}
