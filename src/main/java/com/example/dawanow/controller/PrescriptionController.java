package com.example.dawanow.controller;

import com.example.dawanow.dtos.response.ApiResponse;
import com.example.dawanow.dtos.response.PrescriptionAnalysisResponse;
import com.example.dawanow.service.PrescriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/prescriptions")
@RequiredArgsConstructor
@Tag(name = "Prescriptions", description = "Analyze prescription images and match catalog products")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Analyze a prescription image",
            description = "Extracts medicines and returns conservative matches from the product catalog.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    public ResponseEntity<ApiResponse<PrescriptionAnalysisResponse>> analyze(
            @RequestPart("image") MultipartFile image,
            @RequestParam(defaultValue = "en") String lang,
            @Parameter(
                    name = "X-AI-Api-Key",
                    description = "AI provider API key used only for this prescription analysis request",
                    in = ParameterIn.HEADER,
                    required = false
            )
            @RequestHeader(value = "X-AI-Api-Key", required = false) String aiApiKey
    ) {
        PrescriptionAnalysisResponse response = prescriptionService.analyze(image, lang, aiApiKey);
        return ResponseEntity.ok(ApiResponse.success("Prescription analyzed", response));
    }
}
