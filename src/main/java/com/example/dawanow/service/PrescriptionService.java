package com.example.dawanow.service;

import com.example.dawanow.ai.PrescriptionAiClient;
import com.example.dawanow.dtos.ai.ExtractedPrescription;
import com.example.dawanow.dtos.response.PrescriptionAnalysisResponse;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PrescriptionService {

    private final PrescriptionAiClient prescriptionAiClient;
    private final PrescriptionProductMatchingService matchingService;
    private final MedicineImageValidator imageValidator;

    public PrescriptionAnalysisResponse analyze(MultipartFile image, String lang, String aiApiKey) {
        String language = normalizeLanguage(lang);
        MedicineImageValidator.ValidatedImage validatedImage = imageValidator.read(image, "Prescription");

        ExtractedPrescription extracted = prescriptionAiClient.analyze(
                validatedImage.bytes(),
                validatedImage.contentType(),
                language,
                aiApiKey
        );
        if (extracted == null || extracted.medicines() == null) {
            return new PrescriptionAnalysisResponse(List.of());
        }

        return new PrescriptionAnalysisResponse(
                extracted.medicines().stream()
                        .map(medicine -> matchingService.match(medicine, language))
                        .toList()
        );
    }

    private String normalizeLanguage(String lang) {
        String language = StringUtils.hasText(lang) ? lang.trim().toLowerCase() : "en";
        if (!Set.of("en", "ar").contains(language)) {
            throw new IllegalArgumentException("Language must be either en or ar");
        }
        return language;
    }

}
