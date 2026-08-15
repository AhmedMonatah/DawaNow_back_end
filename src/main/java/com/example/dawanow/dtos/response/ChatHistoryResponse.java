package com.example.dawanow.dtos.response;

import java.util.List;

public record ChatHistoryResponse(
        Long conversationId,
        List<ChatHistoryMessageResponse> messages
) {
}
