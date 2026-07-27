package com.example.dawanow.service;

import com.example.dawanow.ai.PrescriptionAiClient;
import com.example.dawanow.dtos.response.ProductResponse;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MedicineImageSearchService {

    private final PrescriptionAiClient prescriptionAiClient;
    private final PrescriptionProductMatchingService matchingService;
    private final MedicineImageValidator imageValidator;

    public List<ProductResponse> analyzeImage(MultipartFile image, String lang, String aiApiKey) {
        String language = normalizeLanguage(lang);
        MedicineImageValidator.ValidatedImage validatedImage = imageValidator.read(image, "Medicine");

        return prescriptionAiClient.analyzeMedicineImage(
                        validatedImage.bytes(),
                        validatedImage.contentType(),
                        language,
                        aiApiKey
                )
                .map(medicine -> matchingService.findTopProductsByMedicineName(medicine.name(), language))
                .orElseGet(List::of);
    }

    private String normalizeLanguage(String lang) {
        String language = StringUtils.hasText(lang) ? lang.trim().toLowerCase() : "en";
        if (!Set.of("en", "ar").contains(language)) {
            throw new IllegalArgumentException("Language must be either en or ar");
        }
        return language;
    }
}
