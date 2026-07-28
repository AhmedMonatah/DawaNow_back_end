package com.example.dawanow.dtos.response;

import java.math.BigDecimal;

public record PrescriptionCandidateResponse(
        Long productId,
        String name,
        String strength,
        String form,
        BigDecimal price,
        String imageUrl
) {
}
