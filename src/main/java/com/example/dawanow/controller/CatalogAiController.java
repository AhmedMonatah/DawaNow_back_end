package com.example.dawanow.controller;

import com.example.dawanow.dtos.response.ApiResponse;
import com.example.dawanow.dtos.response.ProductResponse;
import com.example.dawanow.service.ai.rag.CatalogRagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/catalog")
@RequiredArgsConstructor
@Validated
@Tag(name = "Catalog RAG", description = "Grounded English/Arabic medicine catalog retrieval and answers")
public class CatalogAiController {

    private final CatalogRagService ragService;

    @GetMapping("/search")
    @Operation(summary = "Hybrid semantic and lexical search over the product catalog")
    public ResponseEntity<ApiResponse<CatalogSearchResponse>> search(
            @Parameter(required = true, example = "paracetamol tablets")
            @RequestParam String query,
            @RequestParam(defaultValue = "en") String lang,
            @RequestParam(required = false) @Min(1) @Max(20) Integer limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Catalog matches retrieved",
                ragService.search(query, lang, limit)
        ));
    }

    @PostMapping("/ask")
    @Operation(summary = "Answer a catalog question using retrieved products as the only context")
    public ResponseEntity<ApiResponse<CatalogAnswerResponse>> ask(
            @Valid @RequestBody CatalogQuestionRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Catalog answer generated", ragService.answer(request)));
    }

    @GetMapping("/index/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Inspect the current catalog embedding index status",
            description = "Admin only. Intended for administrative monitoring.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    public ResponseEntity<ApiResponse<CatalogIndexStatusResponse>> indexStatus() {
        return ResponseEntity.ok(ApiResponse.success("Catalog index status fetched", ragService.status()));
    }

    @PostMapping("/index/refresh")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Refresh changed catalog embeddings without downloading any model",
            description = "Admin only.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    public ResponseEntity<ApiResponse<CatalogIndexStatusResponse>> refreshIndex() {
        return ResponseEntity.ok(ApiResponse.success("Catalog index refreshed", ragService.refresh()));
    }

    public record CatalogQuestionRequest(
            @NotBlank @Size(max = 500) String question,
            @Pattern(regexp = "en|ar", message = "must be en or ar") String lang,
            @Min(1) @Max(20) Integer limit
    ) {
    }

    public record ProductMatchResponse(
            ProductResponse product,
            double score,
            double semanticScore,
            double lexicalScore,
            String matchReason
    ) {
    }

    public record CatalogSearchResponse(
            String query,
            String language,
            boolean semanticSearchUsed,
            String embeddingProvider,
            String embeddingModel,
            List<ProductMatchResponse> matches
    ) {
    }

    public record CatalogAnswerResponse(
            String question,
            String answer,
            String language,
            String generationProvider,
            String generationModel,
            boolean semanticSearchUsed,
            List<ProductMatchResponse> sources
    ) {
    }

    public record CatalogIndexStatusResponse(
            int totalProducts,
            int indexedProducts,
            boolean semanticReady,
            String embeddingProvider,
            String embeddingModel,
            Instant lastRefresh,
            String lastError
    ) {
    }
}
