package com.example.dawanow.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(example = "{\"message\": \"I have a headache\"}")
public record ChatMessageRequest(
        @NotBlank @Size(max = 2000)
        @Schema(description = "The user's message. Language is detected automatically and the reply "
                + "continues the caller's own conversation history.")
        String message
) {
}
