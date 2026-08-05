package com.example.dawanow.controller;

import com.example.dawanow.dtos.response.AiDashboardSummaryResponse;
import com.example.dawanow.dtos.response.ApiResponse;
import com.example.dawanow.entity.DashboardPeriod;
import com.example.dawanow.service.ai.AiDashboardSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PHARMACIST')")
@Tag(name = "AI Dashboard", description = "AI-generated pharmacy dashboard summaries")
public class AiDashboardController {

    private final AiDashboardSummaryService summaryService;

    @GetMapping("/summary")
    @Operation(
            summary = "AI summary of the pharmacy dashboard",
            description = "Summarizes the pharmacy's dashboard metrics in natural language. "
                    + "Only the pharmacy's admin pharmacist can access it (same rule as the "
                    + "dashboard itself). Summaries are cached for a few minutes."
    )
    public ResponseEntity<ApiResponse<AiDashboardSummaryResponse>> getSummary(
            @RequestParam(defaultValue = "LAST_WEEK") DashboardPeriod period,
            @RequestParam(defaultValue = "en") String lang
    ) {
        String language = "ar".equalsIgnoreCase(lang) ? "ar" : "en";
        return ResponseEntity.ok(ApiResponse.success(
                "Dashboard summary generated",
                summaryService.getSummary(period, language)
        ));
    }
}
