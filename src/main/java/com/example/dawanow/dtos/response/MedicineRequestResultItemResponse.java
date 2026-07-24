package com.example.dawanow.dtos.response;

import java.math.BigDecimal;

public record MedicineRequestResultItemResponse(
        Long requestItemId,
        Long productId,
        String productName,
        String imageUrl,
        BigDecimal unitPrice,
        Boolean alternative,
        Boolean available
) {

}
