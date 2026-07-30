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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Stripe PaymentIntent creation and webhooks")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create-intent")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            summary = "Create a Stripe PaymentIntent for an order",
            description = "Amount is calculated from the order in the database. Returns clientSecret for PaymentSheet.",
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

    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Stripe webhook receiver (signature verified)")
    public ResponseEntity<Void> webhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String stripeSignature
    ) {
        paymentService.handleWebhook(payload, stripeSignature);
        return ResponseEntity.ok().build();
    }
}
