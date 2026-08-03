package com.example.dawanow.service.ai.chat;

import com.example.dawanow.config.AiChatProperties;
import com.example.dawanow.controller.CatalogAiController.ProductMatchResponse;
import com.example.dawanow.dtos.request.AddCartItemRequest;
import com.example.dawanow.dtos.response.CartResponse;
import com.example.dawanow.dtos.response.InteractionWarningResponse;
import com.example.dawanow.dtos.response.ProductResponse;
import com.example.dawanow.service.CartService;
import com.example.dawanow.service.ai.interactions.CartInteractionService;
import com.example.dawanow.service.ai.rag.CatalogRagService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Executes "add to cart" requests coming from the AI chat.
 *
 * <p>The product is resolved with the same hybrid catalog search the chat
 * already uses; the cart mutation itself goes through the existing
 * {@link CartService}, which resolves the customer from the security context.
 * A match only executes when it is confident — anything ambiguous comes back
 * as candidates for the user to pick from, because silently adding the wrong
 * medicine is worse than one extra question.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatCartActionService {

    private static final int MAX_QUANTITY = 20;

    private final CartService cartService;
    private final CatalogRagService catalogRagService;
    private final CartInteractionService cartInteractionService;
    private final AiChatProperties properties;

    public enum Status { ADDED, AMBIGUOUS, NOT_FOUND }

    public record CartActionOutcome(
            Status status,
            ProductResponse product,
            List<ProductResponse> candidates,
            int quantity,
            long cartItemCount,
            List<InteractionWarningResponse> interactionWarnings
    ) {
        static CartActionOutcome notFound() {
            return new CartActionOutcome(Status.NOT_FOUND, null, List.of(), 0, 0, List.of());
        }
    }

    public CartActionOutcome addToCart(String searchQuery, Integer requestedQuantity, String language) {
        if (!StringUtils.hasText(searchQuery)) {
            return CartActionOutcome.notFound();
        }
        int quantity = clampQuantity(requestedQuantity);

        List<ProductMatchResponse> matches = search(searchQuery.trim(), language);
        if (matches.isEmpty()) {
            return CartActionOutcome.notFound();
        }

        ProductMatchResponse top = matches.getFirst();
        if (!isConfident(matches)) {
            List<ProductResponse> candidates = matches.stream()
                    .limit(properties.getMaxCartCandidates())
                    .map(ProductMatchResponse::product)
                    .toList();
            return new CartActionOutcome(Status.AMBIGUOUS, null, candidates, quantity, 0, List.of());
        }

        CartResponse cart = cartService.addItem(
                new AddCartItemRequest(top.product().id(), (long) quantity),
                language
        );
        List<InteractionWarningResponse> warnings = interactionWarnings(top.product().id(), language);
        log.info("Chat added product {} x{} to cart {} ({} warning(s))",
                top.product().id(), quantity, cart.id(), warnings.size());
        return new CartActionOutcome(
                Status.ADDED, top.product(), List.of(), quantity, cart.items().size(), warnings);
    }

    /**
     * Trust the match alone only on an exact name hit, or when the hybrid score
     * clears the threshold with a clear lead over the runner-up.
     */
    private boolean isConfident(List<ProductMatchResponse> matches) {
        ProductMatchResponse top = matches.getFirst();
        if ("exact-name".equals(top.matchReason())) {
            return true;
        }
        if (top.score() < properties.getAddToCartMinScore()) {
            return false;
        }
        return matches.size() == 1
                || top.score() - matches.get(1).score() >= properties.getAddToCartMinGap();
    }

    private List<ProductMatchResponse> search(String query, String language) {
        try {
            return catalogRagService.search(query, language, properties.getMaxCartCandidates()).matches();
        } catch (IllegalArgumentException exception) {
            log.warn("Chat cart search skipped: {}", exception.getMessage());
            return List.of();
        }
    }

    /** The warning must never block the add — it decorates the confirmation. */
    private List<InteractionWarningResponse> interactionWarnings(Long productId, String language) {
        try {
            return cartInteractionService.warningsInvolving(productId, language);
        } catch (RuntimeException exception) {
            log.warn("Cart interaction check failed after chat add: {}", exception.getMessage());
            return List.of();
        }
    }

    private int clampQuantity(Integer requested) {
        if (requested == null || requested < 1) {
            return 1;
        }
        return Math.min(requested, MAX_QUANTITY);
    }
}
