package com.example.dawanow.service.ai.chat;

import com.example.dawanow.config.AiChatProperties;
import com.example.dawanow.controller.CatalogAiController.ProductMatchResponse;
import com.example.dawanow.dtos.request.AddCartItemRequest;
import com.example.dawanow.dtos.response.CartResponse;
import com.example.dawanow.dtos.response.InteractionWarningResponse;
import com.example.dawanow.dtos.response.ProductResponse;
import com.example.dawanow.service.CartService;
import com.example.dawanow.service.ai.chat.AiChatModelClient.AgentStep;
import com.example.dawanow.service.ai.chat.AiChatModelClient.GatewayMessage;
import com.example.dawanow.service.ai.interactions.CartInteractionService;
import com.example.dawanow.service.ai.rag.CatalogRagService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Agentic add-to-cart: the model decides its own next step — search the
 * catalog, inspect results, check drug interactions, then add, ask, or stop —
 * seeing each tool's observation before choosing the next one.
 *
 * <p>The loop is deliberately fenced for a medical product:</p>
 * <ul>
 *   <li>its only mutating tool is a REVERSIBLE cart add — placing the actual
 *       order (CREATE_REQUEST) is not in the tool set and always needs a human;</li>
 *   <li>the model can only reference product ids that appeared in its own
 *       search observations this run ({@code allowedProductIds});</li>
 *   <li>a HIGH-severity interaction blocks the add in Java, regardless of what
 *       the model decided;</li>
 *   <li>at most one add per run, at most {@code agentMaxSteps} decisions, and
 *       any failure aborts to the deterministic single-shot path.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CartAgentService {

    private static final int MAX_QUANTITY = 20;
    private static final int STEP_MAX_TOKENS = 250;
    private static final int SEARCH_RESULT_LIMIT = 5;
    private static final String SEVERITY_HIGH = "HIGH";

    private final CatalogRagService catalogRagService;
    private final CartService cartService;
    private final CartInteractionService cartInteractionService;
    private final AiChatModelClient modelClient;
    private final AiChatPromptFactory promptFactory;
    private final AiChatProperties properties;

    public enum Status { ADDED, ASK_USER, DONE, FALLBACK }

    public record AgentOutcome(
            Status status,
            ProductResponse product,
            int quantity,
            long cartItemCount,
            List<InteractionWarningResponse> warnings,
            String reply,
            List<ProductResponse> candidates,
            int stepsUsed
    ) {
        static AgentOutcome fallback() {
            return new AgentOutcome(Status.FALLBACK, null, 0, 0, List.of(), null, List.of(), 0);
        }
    }

    public AgentOutcome run(String userMessage, Integer requestedQuantity, String language) {
        try {
            return runLoop(userMessage, requestedQuantity, language);
        } catch (RuntimeException exception) {
            // Gateway down, parse chaos, anything unexpected: the deterministic
            // single-shot path takes over. The agent must never make add-to-cart
            // LESS reliable than it was without it.
            log.warn("Cart agent aborted, falling back to deterministic path: {}",
                    exception.getMessage());
            return AgentOutcome.fallback();
        }
    }

    private AgentOutcome runLoop(String userMessage, Integer requestedQuantity, String language) {
        List<String> transcript = new ArrayList<>();
        Set<Long> allowedProductIds = new LinkedHashSet<>();
        Map<Long, ProductResponse> knownProducts = new HashMap<>();
        List<ProductResponse> lastCandidates = List.of();
        int invalidSteps = 0;

        for (int stepNumber = 1; stepNumber <= properties.getAgentMaxSteps(); stepNumber++) {
            AgentStep step = modelClient.agentStep(
                    promptFactory.cartAgentSystemPrompt(),
                    List.of(new GatewayMessage("user", buildTurn(userMessage, transcript))),
                    STEP_MAX_TOKENS
            );
            String tool = step.tool() == null ? "" : step.tool().trim().toLowerCase(Locale.ROOT);
            log.info("Cart agent step {}: tool={}", stepNumber, tool);

            switch (tool) {
                case "search_catalog" -> {
                    if (!StringUtils.hasText(step.query())) {
                        transcript.add("search_catalog -> ERROR: empty query");
                        continue;
                    }
                    List<ProductMatchResponse> matches = search(step.query().trim(), language);
                    lastCandidates = matches.stream().map(ProductMatchResponse::product).toList();
                    lastCandidates.forEach(product -> {
                        allowedProductIds.add(product.id());
                        knownProducts.put(product.id(), product);
                    });
                    transcript.add("search_catalog(\"" + step.query().trim() + "\") -> "
                            + describeMatches(matches));
                }

                case "check_interactions" -> {
                    Long productId = step.productId();
                    if (productId == null || !allowedProductIds.contains(productId)) {
                        transcript.add("check_interactions -> ERROR: unknown productId, "
                                + "use an id from your search results");
                        continue;
                    }
                    List<InteractionWarningResponse> warnings =
                            cartInteractionService.previewWarningsForCandidate(productId, language);
                    transcript.add("check_interactions(" + productId + ") -> "
                            + describeWarnings(warnings));
                }

                case "add_to_cart" -> {
                    Long productId = step.productId();
                    if (productId == null || !allowedProductIds.contains(productId)) {
                        transcript.add("add_to_cart -> ERROR: unknown productId, "
                                + "only ids from your search results are allowed");
                        if (++invalidSteps >= 2) {
                            return AgentOutcome.fallback();
                        }
                        continue;
                    }
                    ProductResponse product = knownProducts.get(productId);
                    int quantity = clampQuantity(
                            requestedQuantity != null ? requestedQuantity : step.quantity());

                    // Deterministic safety gate — not the model's call to make.
                    List<InteractionWarningResponse> warnings =
                            cartInteractionService.previewWarningsForCandidate(productId, language);
                    InteractionWarningResponse blocking = warnings.stream()
                            .filter(warning -> SEVERITY_HIGH.equals(warning.severity()))
                            .findFirst()
                            .orElse(null);
                    if (blocking != null) {
                        return new AgentOutcome(
                                Status.ASK_USER, null, 0, 0, warnings,
                                promptFactory.interactionBlockedReply(
                                        language, product.name(), blocking.title()),
                                List.of(product), stepNumber);
                    }

                    CartResponse cart = cartService.addItem(
                            new AddCartItemRequest(productId, (long) quantity), language);
                    return new AgentOutcome(
                            Status.ADDED, product, quantity, cart.items().size(),
                            warnings, null, List.of(), stepNumber);
                }

                case "ask_user" -> {
                    if (!StringUtils.hasText(step.question())) {
                        return AgentOutcome.fallback();
                    }
                    return new AgentOutcome(
                            Status.ASK_USER, null, 0, 0, List.of(),
                            step.question().trim(),
                            limitCandidates(lastCandidates), stepNumber);
                }

                case "done" -> {
                    return new AgentOutcome(
                            Status.DONE, null, 0, 0, List.of(),
                            StringUtils.hasText(step.summary()) ? step.summary().trim() : null,
                            limitCandidates(lastCandidates), stepNumber);
                }

                default -> {
                    transcript.add("-> ERROR: unknown tool, pick one of search_catalog, "
                            + "check_interactions, add_to_cart, ask_user, done");
                    if (++invalidSteps >= 2) {
                        return AgentOutcome.fallback();
                    }
                }
            }
        }
        log.info("Cart agent hit the {}-step ceiling without finishing", properties.getAgentMaxSteps());
        return AgentOutcome.fallback();
    }

    /** The whole run so far, restated inline — same trick as chat history. */
    private String buildTurn(String userMessage, List<String> transcript) {
        StringBuilder turn = new StringBuilder("[User request]\n").append(userMessage);
        if (!transcript.isEmpty()) {
            turn.append("\n\n[Your steps so far, oldest first]\n");
            for (int index = 0; index < transcript.size(); index++) {
                turn.append(index + 1).append(". ").append(transcript.get(index)).append('\n');
            }
        }
        turn.append("\n[Choose your next tool]");
        return turn.toString();
    }

    private List<ProductMatchResponse> search(String query, String language) {
        try {
            return catalogRagService.search(query, language, SEARCH_RESULT_LIMIT).matches();
        } catch (IllegalArgumentException exception) {
            return List.of();
        }
    }

    private String describeMatches(List<ProductMatchResponse> matches) {
        if (matches.isEmpty()) {
            return "no results";
        }
        StringBuilder text = new StringBuilder(matches.size() + " result(s):");
        for (ProductMatchResponse match : matches) {
            ProductResponse product = match.product();
            text.append("\n  id=").append(product.id())
                    .append(" name=\"").append(product.name()).append('"')
                    .append(" ingredient=").append(value(product.scientificName()))
                    .append(" score=").append(String.format(Locale.ROOT, "%.2f", match.score()));
        }
        return text.toString();
    }

    private String describeWarnings(List<InteractionWarningResponse> warnings) {
        if (warnings.isEmpty()) {
            return "no interactions with the current cart";
        }
        StringBuilder text = new StringBuilder(warnings.size() + " warning(s):");
        warnings.forEach(warning -> text.append("\n  [").append(warning.severity()).append("] ")
                .append(warning.title()));
        return text.toString();
    }

    private List<ProductResponse> limitCandidates(List<ProductResponse> candidates) {
        return candidates.stream().limit(properties.getMaxCartCandidates()).toList();
    }

    private int clampQuantity(Integer requested) {
        if (requested == null || requested < 1) {
            return 1;
        }
        return Math.min(requested, MAX_QUANTITY);
    }

    private String value(String text) {
        return text == null ? "" : text;
    }
}
