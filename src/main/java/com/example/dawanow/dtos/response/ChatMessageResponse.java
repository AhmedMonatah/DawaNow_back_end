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
        String disclaimer,
        ChatActionResponse action
) {
}
