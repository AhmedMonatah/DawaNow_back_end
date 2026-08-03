package com.example.dawanow.dtos.response;

import java.time.LocalDateTime;
import java.util.List;

public record ChatHistoryMessageResponse(
        Long id,
        String role,
        String content,
        String intent,
        List<ProductResponse> products,
        List<ProductResponse> alternatives,
        List<String> doctorSpecializations,
        List<EmergencyNumberResponse> emergencyNumbers,
        List<ChatCategoryResponse> categories,
        List<PharmacistRankingResponse> pharmacistRankings,
        LocalDateTime createdAt
) {
}
