package com.example.dawanow.dtos.response;

import java.util.List;

public record ChatMessageResponse(
        Long conversationId,
        Long messageId,
        String intent,
        String answer,
        List<ProductResponse> products,
        List<ProductResponse> alternatives,
        List<String> doctorSpecializations,
        List<EmergencyNumberResponse> emergencyNumbers,
        List<ChatCategoryResponse> categories,
        List<PharmacistRankingResponse> pharmacistRankings,
        String disclaimer,
        ChatActionResponse action,
        ReminderResponse reminder,
        ChatAnalyticsResponse analytics
) {
    public ChatMessageResponse(
            Long conversationId,
            Long messageId,
            String intent,
            String answer,
            List<ProductResponse> products,
            List<ProductResponse> alternatives,
            List<String> doctorSpecializations,
            List<EmergencyNumberResponse> emergencyNumbers,
            List<ChatCategoryResponse> categories,
            List<PharmacistRankingResponse> pharmacistRankings,
            String disclaimer,
            ChatActionResponse action,
            ReminderResponse reminder
    ) {
        this(conversationId, messageId, intent, answer, products, alternatives,
                doctorSpecializations, emergencyNumbers, categories, pharmacistRankings,
                disclaimer, action, reminder, null);
    }
}
