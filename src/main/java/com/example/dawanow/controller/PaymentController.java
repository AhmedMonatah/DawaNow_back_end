package com.example.dawanow.controller;

import com.example.dawanow.dtos.request.CreatePaymentIntentRequest;
import com.example.dawanow.dtos.response.ApiResponse;
import com.example.dawanow.dtos.response.PaymentIntentResponse;
import com.example.dawanow.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Create and inspect Stripe payment intents")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/intent")
    @Operation(
            summary = "Create a Stripe payment intent",
            description = "Creates a PaymentIntent and returns the client secret for Stripe.js / mobile SDK confirmation.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    public ResponseEntity<ApiResponse<PaymentIntentResponse>> createIntent(
            @Valid @RequestBody CreatePaymentIntentRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Payment intent created",
                paymentService.createPaymentIntent(request)
        ));
    }

    @GetMapping("/intent/{paymentIntentId}")
    @Operation(
            summary = "Get a Stripe payment intent status",
            description = "Retrieves the current status of an existing PaymentIntent.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    public ResponseEntity<ApiResponse<PaymentIntentResponse>> getIntent(
            @PathVariable String paymentIntentId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Payment intent fetched",
                paymentService.getPaymentIntent(paymentIntentId)
        ));
    }
}
