package com.example.dawanow.service.ai.interactions;

import com.example.dawanow.dtos.response.CartInteractionResponse;
import com.example.dawanow.dtos.response.InteractionWarningResponse;
import com.example.dawanow.dtos.response.InvolvedProductResponse;
import com.example.dawanow.entity.Cart;
import com.example.dawanow.entity.CartItem;
import com.example.dawanow.entity.Product;
import com.example.dawanow.repo.ProductRepository;
import com.example.dawanow.service.CartService;
import com.example.dawanow.service.ai.interactions.DrugInteractionEvaluator.InteractionWarning;
import com.example.dawanow.service.ai.interactions.DrugInteractionEvaluator.ProductIngredients;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluates the current customer's cart against the deterministic interaction
 * rules. Read-only and model-free: warnings are pre-written localized strings.
 */
@Service
@RequiredArgsConstructor
public class CartInteractionService {

    /**
     * Routes with negligible systemic absorption; a topical NSAID gel or an eye
     * drop must not trigger the same warnings as a tablet.
     */
    private static final Set<String> NON_SYSTEMIC_ROUTES = Set.of("TOPICAL", "EYE", "EAR", "SPRAY");

    private final CartService cartService;
    private final IngredientNormalizer normalizer;
    private final DrugInteractionEvaluator evaluator;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public CartInteractionResponse getWarningsForCurrentCart(String language) {
        return new CartInteractionResponse(evaluateCurrentCart(language));
    }

    /**
     * Warnings for the current cart that involve the given product — used by
     * the chat right after it adds something, so the reply can carry the alert.
     */
    @Transactional(readOnly = true)
    public List<InteractionWarningResponse> warningsInvolving(Long productId, String language) {
        return evaluateCurrentCart(language).stream()
                .filter(warning -> warning.involvedProducts().stream()
                        .anyMatch(product -> product.productId().equals(productId)))
                .toList();
    }

    /**
     * Warnings the given candidate WOULD create against the current cart,
     * evaluated without mutating anything — the cart agent's pre-add gate.
     */
    @Transactional(readOnly = true)
    public List<InteractionWarningResponse> previewWarningsForCandidate(
            Long candidateProductId,
            String language
    ) {
        Product candidate = productRepository.findById(candidateProductId).orElse(null);
        if (candidate == null || isNonSystemic(candidate.getRoute())) {
            return List.of();
        }
        List<String> candidateIngredients = normalizer.extractIngredients(candidate.getScientificName());
        if (candidateIngredients.isEmpty()) {
            return List.of();
        }

        List<ProductIngredients> products = collectCartProducts().stream()
                .filter(product -> !product.productId().equals(candidateProductId))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        products.add(new ProductIngredients(
                candidate.getId(), candidate.getName(), candidateIngredients));

        return evaluator.evaluate(products).stream()
                .filter(warning -> warning.products().stream()
                        .anyMatch(product -> product.productId().equals(candidateProductId)))
                .map(warning -> toResponse(warning, language))
                .toList();
    }

    private List<InteractionWarningResponse> evaluateCurrentCart(String language) {
        return evaluator.evaluate(collectCartProducts()).stream()
                .map(warning -> toResponse(warning, language))
                .toList();
    }

    private List<ProductIngredients> collectCartProducts() {
        Cart cart = cartService.getCartEntity();
        List<ProductIngredients> products = new ArrayList<>();
        for (CartItem item : cart.getItems()) {
            Product product = item.getProduct();
            if (product == null || isNonSystemic(product.getRoute())) {
                continue;
            }
            List<String> ingredients = normalizer.extractIngredients(product.getScientificName());
            if (!ingredients.isEmpty()) {
                products.add(new ProductIngredients(product.getId(), product.getName(), ingredients));
            }
        }
        return products;
    }

    private boolean isNonSystemic(String route) {
        return route != null && NON_SYSTEMIC_ROUTES.contains(route.trim().toUpperCase(Locale.ROOT));
    }

    private InteractionWarningResponse toResponse(InteractionWarning warning, String language) {
        return new InteractionWarningResponse(
                warning.rule().severity().name(),
                warning.rule().title(language),
                warning.rule().advice(language),
                warning.products().stream()
                        .map(product -> new InvolvedProductResponse(
                                product.productId(), product.productName(), product.ingredient()))
                        .toList()
        );
    }
}
