package com.example.dawanow.dtos.response;

import java.util.List;

public record CartInteractionResponse(List<InteractionWarningResponse> warnings) {
}
