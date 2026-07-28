package com.example.dawanow.service;

import com.example.dawanow.config.StripeProperties;
import com.example.dawanow.dtos.request.CreatePaymentIntentRequest;
import com.example.dawanow.dtos.response.PaymentIntentResponse;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final StripeProperties stripeProperties;

    public PaymentIntentResponse createPaymentIntent(CreatePaymentIntentRequest request) {
        requireConfigured();

        String currency = StringUtils.hasText(request.currency())
                ? request.currency().trim().toLowerCase(Locale.ROOT)
                : stripeProperties.getCurrency();

        try {
            Stripe.apiKey = stripeProperties.getSecretKey().trim();

            PaymentIntentCreateParams.Builder params = PaymentIntentCreateParams.builder()
                    .setAmount(request.amount())
                    .setCurrency(currency)
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build()
                    );

            if (request.orderId() != null) {
                params.putMetadata("orderId", String.valueOf(request.orderId()));
            }

            PaymentIntent intent = PaymentIntent.create(params.build());
            return toResponse(intent);
        } catch (StripeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Stripe payment intent creation failed: " + exception.getMessage(),
                    exception
            );
        }
    }

    public PaymentIntentResponse getPaymentIntent(String paymentIntentId) {
        requireConfigured();
        if (!StringUtils.hasText(paymentIntentId)) {
            throw new IllegalArgumentException("Payment intent id is required");
        }

        try {
            Stripe.apiKey = stripeProperties.getSecretKey().trim();
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId.trim());
            return toResponse(intent);
        } catch (StripeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Stripe payment intent lookup failed: " + exception.getMessage(),
                    exception
            );
        }
    }

    private PaymentIntentResponse toResponse(PaymentIntent intent) {
        return new PaymentIntentResponse(
                intent.getId(),
                intent.getClientSecret(),
                stripeProperties.getPublishableKey(),
                intent.getAmount(),
                intent.getCurrency(),
                intent.getStatus()
        );
    }

    private void requireConfigured() {
        if (!StringUtils.hasText(stripeProperties.getSecretKey())
                || !StringUtils.hasText(stripeProperties.getPublishableKey())) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Stripe is not configured; set STRIPE_SECRET_KEY and STRIPE_PUBLISHABLE_KEY"
            );
        }
    }
}
