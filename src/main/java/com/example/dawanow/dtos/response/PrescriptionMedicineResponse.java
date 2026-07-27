package com.example.dawanow.dtos.response;

import java.util.List;
import java.util.UUID;

public record PrescriptionMedicineResponse(
        UUID localItemId,
        String rawText,
        String extractedName,
        String extractedStrength,
        String extractedForm,
        PrescriptionMatchStatus matchStatus,
        double confidence,
        List<PrescriptionCandidateResponse> candidates
) {
}
