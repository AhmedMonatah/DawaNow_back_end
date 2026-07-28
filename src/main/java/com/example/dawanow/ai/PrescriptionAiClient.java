package com.example.dawanow.ai;

import com.example.dawanow.dtos.ai.ExtractedMedicine;
import com.example.dawanow.dtos.ai.ExtractedPrescription;
import java.util.Optional;

public interface PrescriptionAiClient {

    ExtractedPrescription analyze(byte[] image, String contentType, String language, String apiKey);

    Optional<ExtractedMedicine> analyzeMedicineImage(byte[] image, String contentType, String language, String apiKey);
}
