package com.example.dawanow.ai;

import com.example.dawanow.dtos.ai.ExtractedPrescription;
import com.example.dawanow.exception.PrescriptionAiUnavailableException;
import com.example.dawanow.dtos.ai.ExtractedMedicine;
import java.util.Optional;

public class UnavailablePrescriptionAiClient implements PrescriptionAiClient {

    @Override
    public ExtractedPrescription analyze(byte[] image, String contentType, String language, String apiKey) {
        throw new PrescriptionAiUnavailableException("Prescription AI is not configured");
    }

    @Override
    public Optional<ExtractedMedicine> analyzeMedicineImage(
            byte[] image, String contentType, String language, String apiKey
    ) {
        throw new PrescriptionAiUnavailableException("Prescription AI is not configured");
    }
}
