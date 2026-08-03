package com.example.dawanow.dtos.response;

import java.time.Instant;

public record AiDashboardSummaryResponse(
        String period,
        String language,
        String summary,
        Instant generatedAt,
        boolean cached
) {
}
