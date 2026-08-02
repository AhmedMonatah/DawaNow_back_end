package com.example.dawanow.service;

import com.example.dawanow.config.StripeProperties;
import com.example.dawanow.dtos.request.CreatePaymentIntentRequest;
import com.example.dawanow.dtos.response.PaymentIntentResponse;
import com.example.dawanow.entity.*;
import com.example.dawanow.exception.ResourceNotFoundException;
import com.example.dawanow.repo.MasterOrderRepository;
import com.example.dawanow.repo.OrderRepository;
import com.example.dawanow.repo.StripeWebhookEventRepository;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final StripeProperties stripeProperties;
    private final MasterOrderRepository masterOrderRepository;
    private final StripeWebhookEventRepository stripeWebhookEventRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional
    public PaymentIntentResponse createPaymentIntent(CreatePaymentIntentRequest request) {
        requireSecretConfigured();

        User user = currentUserProvider.get();
        MasterOrder order = masterOrderRepository.findById(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + request.orderId()));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Master Order does not belong to the authenticated user");
        }

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new IllegalArgumentException("Order is already paid");
        }

        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Cannot pay for a cancelled order");
        }

        if (order.getPaymentMethod() != PaymentMethod.CARD) {
            throw new IllegalArgumentException(
                    "Order is set to pay with cash; Stripe payment is not available for this order"
            );
        }

        // Reuse an unfinished PaymentIntent when possible.
        if (StringUtils.hasText(order.getPaymentIntentId())
                && order.getPaymentStatus() == PaymentStatus.PENDING) {
            try {
                Stripe.apiKey = stripeProperties.getSecretKey().trim();
                PaymentIntent existing = PaymentIntent.retrieve(order.getPaymentIntentId());
                String status = existing.getStatus();
                if ("requires_payment_method".equals(status)
                        || "requires_confirmation".equals(status)
                        || "requires_action".equals(status)) {
                    return new PaymentIntentResponse(existing.getId(), existing.getClientSecret());
                }
            } catch (StripeException exception) {
                log.warn("Could not reuse payment intent {}: {}", order.getPaymentIntentId(), exception.getMessage());
            }
        }

        long amountInMinorUnits = toMinorUnits(order.getTotalPrice());
        if (amountInMinorUnits < 1) {
            throw new IllegalArgumentException("Order payable amount must be greater than zero");
        }

        try {
            Stripe.apiKey = stripeProperties.getSecretKey().trim();

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInMinorUnits)
                    .setCurrency(stripeProperties.getCurrency().trim().toLowerCase(Locale.ROOT))
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build()
                    )
                    .putMetadata("orderId", String.valueOf(order.getId()))
                    .putMetadata("userId", String.valueOf(user.getId()))
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);

            order.setPaymentIntentId(intent.getId());
            order.setPaymentStatus(PaymentStatus.PENDING);
            masterOrderRepository.save(order);

            log.info("Created PaymentIntent {} for order {} (user {})", intent.getId(), order.getId(), user.getId());
            return new PaymentIntentResponse(intent.getId(), intent.getClientSecret());
        } catch (StripeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Stripe payment intent creation failed: " + exception.getMessage(),
                    exception
            );
        }
    }

    @Transactional
    public void handleWebhook(String payload, String stripeSignatureHeader) {
        requireWebhookConfigured();

        Event event;
        try {
            event = Webhook.constructEvent(
                    payload,
                    stripeSignatureHeader,
                    stripeProperties.getWebhookSecret().trim()
            );
        } catch (SignatureVerificationException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Stripe webhook signature", exception);
        }

        if (stripeWebhookEventRepository.existsById(event.getId())) {
            log.info("Ignoring duplicate Stripe event {}", event.getId());
            return;
        }

        switch (event.getType()) {
            case "payment_intent.succeeded" -> handlePaymentIntentSucceeded(event);
            case "payment_intent.payment_failed" -> handlePaymentIntentFailed(event);
            case "payment_intent.canceled" -> handlePaymentIntentCanceled(event);
            default -> log.debug("Unhandled Stripe event type: {}", event.getType());
        }

        StripeWebhookEvent processed = new StripeWebhookEvent();
        processed.setEventId(event.getId());
        processed.setEventType(event.getType());
        processed.setProcessedAt(LocalDateTime.now());
        stripeWebhookEventRepository.save(processed);
    }

    private void handlePaymentIntentSucceeded(Event event) {
        PaymentIntent paymentIntent = requirePaymentIntent(event);
        MasterOrder order = findOrderForPaymentIntent(paymentIntent);

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            log.info("Order {} already PAID; skipping duplicate success handling", order.getId());
            return;
        }

        order.setPaymentStatus(PaymentStatus.PAID);
        order.setPaymentIntentId(paymentIntent.getId());
        order.setPaymentMethod(PaymentMethod.CARD);
        order.setPaidAt(LocalDateTime.now());
        masterOrderRepository.save(order);
        log.info("Order {} marked PAID via webhook {}", order.getId(), event.getId());
    }

    private void handlePaymentIntentFailed(Event event) {
        PaymentIntent paymentIntent = requirePaymentIntent(event);
        MasterOrder order = findOrderForPaymentIntent(paymentIntent);

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            log.warn("Ignoring failure event for already-paid order {}", order.getId());
            return;
        }

        order.setPaymentStatus(PaymentStatus.FAILED);
        order.setPaymentIntentId(paymentIntent.getId());
        masterOrderRepository.save(order);
        log.info("Order {} marked FAILED via webhook {}", order.getId(), event.getId());
    }

    private void handlePaymentIntentCanceled(Event event) {
        PaymentIntent paymentIntent = requirePaymentIntent(event);
        MasterOrder order = findOrderForPaymentIntent(paymentIntent);

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            log.warn("Ignoring cancel event for already-paid order {}", order.getId());
            return;
        }

        order.setPaymentStatus(PaymentStatus.CANCELED);
        order.setPaymentIntentId(paymentIntent.getId());
        masterOrderRepository.save(order);
        log.info("Order {} marked CANCELED via webhook {}", order.getId(), event.getId());
    }

    private MasterOrder findOrderForPaymentIntent(PaymentIntent paymentIntent) {
        MasterOrder byIntent = masterOrderRepository.findByPaymentIntentId(paymentIntent.getId()).orElse(null);
        if (byIntent != null) {
            return byIntent;
        }

        Map<String, String> metadata = paymentIntent.getMetadata();
        String orderIdValue = metadata != null ? metadata.get("orderId") : null;
        if (!StringUtils.hasText(orderIdValue)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "PaymentIntent missing orderId metadata: " + paymentIntent.getId()
            );
        }

        Long orderId;
        try {
            orderId = Long.valueOf(orderIdValue);
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid orderId metadata on PaymentIntent: " + paymentIntent.getId()
            );
        }

        return masterOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found for webhook: " + orderId));
    }

    private PaymentIntent requirePaymentIntent(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        StripeObject stripeObject = deserializer.getObject()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unable to deserialize Stripe event object: " + event.getId()
                ));

        if (!(stripeObject instanceof PaymentIntent paymentIntent)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Expected PaymentIntent for event: " + event.getId()
            );
        }
        return paymentIntent;
    }

    private long toMinorUnits(BigDecimal amount) {
        return amount
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact();
    }

    private void requireSecretConfigured() {
        if (!StringUtils.hasText(stripeProperties.getSecretKey())) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Stripe is not configured; set STRIPE_SECRET_KEY"
            );
        }
    }

    private void requireWebhookConfigured() {
        if (!StringUtils.hasText(stripeProperties.getWebhookSecret())) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Stripe webhook is not configured; set STRIPE_WEBHOOK_SECRET"
            );
        }
    }
}
