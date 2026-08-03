package com.example.dawanow.controller;

import com.example.dawanow.dtos.response.ApiResponse;
import com.example.dawanow.dtos.response.CartInteractionResponse;
import com.example.dawanow.service.ai.interactions.CartInteractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deterministic drug-interaction warnings for the current customer's cart.
 * Rule-based (no AI model involved), so the answer is instant and repeatable.
 */
@RestController
@RequestMapping("/api/v1/cart/interactions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
@Tag(name = "Cart Interactions", description = "Drug-interaction warnings for the cart")
public class CartInteractionController {

    private final CartInteractionService cartInteractionService;

    @GetMapping
    @Operation(
            summary = "Check the cart for drug interactions",
            description = "Evaluates the current customer's cart against curated interaction rules "
                    + "(by active ingredient) and returns localized warnings, most severe first. "
                    + "Informational only — not a substitute for a pharmacist's review."
    )
    public ResponseEntity<ApiResponse<CartInteractionResponse>> getInteractions(
            @RequestParam(defaultValue = "en") String lang
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Cart interactions evaluated",
                cartInteractionService.getWarningsForCurrentCart(normalize(lang))
        ));
    }

    private String normalize(String lang) {
        return "ar".equalsIgnoreCase(lang) ? "ar" : "en";
    }
}
