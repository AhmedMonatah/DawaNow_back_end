package com.example.dawanow.dtos.ai;

public record ExtractedMedicine(
        String rawText,
        String name,
        String strength,
        String form,
        double confidence
) {
}
