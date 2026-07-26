package com.example.dawanow.service;

import com.example.dawanow.controller.CatalogAiController.ProductMatchResponse;
import com.example.dawanow.dtos.ai.ExtractedMedicine;
import com.example.dawanow.dtos.response.PrescriptionCandidateResponse;
import com.example.dawanow.dtos.response.PrescriptionMatchStatus;
import com.example.dawanow.dtos.response.PrescriptionMedicineResponse;
import com.example.dawanow.dtos.response.ProductResponse;
import com.example.dawanow.service.ai.rag.CatalogRagService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PrescriptionProductMatchingService {

    private static final double MIN_READABLE_CONFIDENCE = 0.5;
    private static final int MAX_CANDIDATES = 3;

    private final CatalogRagService ragService;

    public List<ProductResponse> findTopProductsByMedicineName(String medicineName, String language) {
        if (!StringUtils.hasText(medicineName)) {
            return List.of();
        }

        return ragService.search(medicineName.trim(), language, MAX_CANDIDATES)
                .matches()
                .stream()
                .map(ProductMatchResponse::product)
                .toList();
    }

    public PrescriptionMedicineResponse match(ExtractedMedicine medicine, String language) {
        if (medicine == null
                || medicine.confidence() < MIN_READABLE_CONFIDENCE
                || !StringUtils.hasText(medicine.name())) {
            return response(medicine, PrescriptionMatchStatus.UNREADABLE, List.of());
        }

        List<ProductMatchResponse> matches = ragService.search(searchQuery(medicine), language, MAX_CANDIDATES)
                .matches();

        PrescriptionMatchStatus status = matches.isEmpty()
                ? PrescriptionMatchStatus.NOT_FOUND
                : matchStatus(matches);
        return response(medicine, status, matches);
    }

    private PrescriptionMedicineResponse response(
            ExtractedMedicine medicine,
            PrescriptionMatchStatus status,
            List<ProductMatchResponse> candidates
    ) {
        return new PrescriptionMedicineResponse(
                UUID.randomUUID(),
                medicine == null ? null : medicine.rawText(),
                medicine == null ? null : medicine.name(),
                medicine == null ? null : medicine.strength(),
                medicine == null ? null : medicine.form(),
                status,
                medicine == null ? 0 : medicine.confidence(),
                candidates.stream().map(this::toResponse).toList()
        );
    }

    private PrescriptionMatchStatus matchStatus(List<ProductMatchResponse> matches) {
        if (matches.size() == 1 && matches.getFirst().score() >= 0.95) {
            return PrescriptionMatchStatus.MATCHED;
        }
        return PrescriptionMatchStatus.NEEDS_REVIEW;
    }

    private String searchQuery(ExtractedMedicine medicine) {
        StringBuilder query = new StringBuilder(medicine.name().trim());
        appendIfPresent(query, medicine.strength());
        appendIfPresent(query, medicine.form());
        return query.toString();
    }

    private void appendIfPresent(StringBuilder query, String value) {
        if (StringUtils.hasText(value)) {
            query.append(' ').append(value.trim());
        }
    }

    private PrescriptionCandidateResponse toResponse(ProductMatchResponse match) {
        ProductResponse product = match.product();
        return new PrescriptionCandidateResponse(
                product.id(),
                product.name(),
                product.strength(),
                product.form(),
                product.price(),
                product.imageUrl()
        );
    }
}
