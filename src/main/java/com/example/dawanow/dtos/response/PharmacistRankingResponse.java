package com.example.dawanow.dtos.response;

import java.util.List;

public record PharmacistRankingResponse(
        String metric,
        String period,
        List<PharmacistPerformanceEntryResponse> entries
) {
}
