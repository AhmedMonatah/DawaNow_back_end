package com.example.dawanow.dtos.response;

public record PharmacistPerformanceEntryResponse(
        int rank,
        Long pharmacistId,
        String firstName,
        String lastName,
        long count
) {
}
