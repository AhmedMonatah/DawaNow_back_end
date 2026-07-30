package com.example.dawanow.dtos.response;

import java.math.BigDecimal;

public record MedicineRequestResultItemResponse(
        Long requestItemId,
        ProductSummaryResponse product,
        BigDecimal unitPrice,
        Boolean alternative,
        Boolean available
) {

}
