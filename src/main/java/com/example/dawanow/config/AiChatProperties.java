package com.example.dawanow.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "dawanow.ai.chat")
@Validated
@Getter
@Setter
public class AiChatProperties {

    @Min(1)
    @Max(5)
    private int maxSuggestedProducts = 3;

    /**
     * Deliberately lower than {@link #maxSuggestedProducts}: when the user only
     * described a symptom, the answer should lead with self-care and mention a
     * couple of options at most.
     */
    @Min(1)
    @Max(3)
    private int maxSymptomProducts = 2;

    @Min(1)
    @Max(30)
    private int historyWindow = 10;

    /**
     * Arabic answers use noticeably more tokens per word than English, and the
     * symptom template asks for several sections, so this is well above the
     * catalog-answer budget to avoid truncated JSON.
     */
    @Min(100)
    @Max(4000)
    private int maxTokens = 1400;

    @Min(1)
    @Max(20)
    private int retrievalTopK = 8;

    /**
     * The student gateway frequently refuses the first TCP connection, so the
     * connect timeout is generous and transient failures are retried instead of
     * being surfaced to the app as an immediate 503.
     */
    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(30);

    @Min(1)
    @Max(5)
    private int maxAttempts = 3;

    @NotNull
    private Duration retryDelay = Duration.ofSeconds(2);

    /**
     * A chat add-to-cart only executes when the top catalog match clears this
     * hybrid score; anything weaker asks the user to pick from candidates
     * instead of silently adding the wrong medicine.
     */
    @Min(0)
    @Max(1)
    private double addToCartMinScore = 0.80;

    /** Minimum lead over the runner-up before the top match is trusted alone. */
    @Min(0)
    @Max(1)
    private double addToCartMinGap = 0.10;

    @Min(2)
    @Max(5)
    private int maxCartCandidates = 3;

    @NotNull
    private Duration dashboardSummaryTtl = Duration.ofMinutes(10);

    /**
     * Hard ceiling on cart-agent decision steps per run. The loop aborts to the
     * deterministic single-shot path when the model hasn't finished by then.
     */
    @Min(2)
    @Max(10)
    private int agentMaxSteps = 6;
}
