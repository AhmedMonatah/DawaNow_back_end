package com.example.dawanow.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(example = "{\"message\": \"Show this month's pharmacy overview\", "
        + "\"analyticsPreset\": \"PHARMACY_MONTH_OVERVIEW\"}")
public record ChatMessageRequest(
        @NotBlank @Size(max = 2000)
        @Schema(description = "The user's message. Language is detected automatically and the reply "
                + "continues the caller's own conversation history.")
        String message,
        @Schema(description = "Optional allow-listed pharmacist quick-action preset. Ordinary typed "
                + "messages omit it. No pharmacy or pharmacist id is accepted from the client.")
        String analyticsPreset
) {
    public ChatMessageRequest(String message) {
        this(message, null);
    }
}
