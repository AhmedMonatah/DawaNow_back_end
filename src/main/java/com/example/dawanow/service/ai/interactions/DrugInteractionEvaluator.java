package com.example.dawanow.service.ai.interactions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Pure pairwise rule engine over the ingredients present in a cart.
 *
 * <p>Rules only fire ACROSS different products: a fixed combination such as
 * CAFFEINE+PARACETAMOL inside one box must never warn against itself. Each rule
 * emits at most one warning listing every involved product, sorted HIGH before
 * MODERATE.</p>
 */
@Component
@RequiredArgsConstructor
public class DrugInteractionEvaluator {

    private final DrugInteractionRuleService ruleService;

    public record ProductIngredients(Long productId, String productName, List<String> ingredients) {
    }

    public record InvolvedProduct(Long productId, String productName, String ingredient) {
    }

    public record InteractionWarning(InteractionRule rule, List<InvolvedProduct> products) {
    }

    public List<InteractionWarning> evaluate(List<ProductIngredients> products) {
        if (products.size() < 2) {
            return List.of();
        }

        // ingredient -> products carrying it, and class -> products carrying it
        Map<String, List<InvolvedProduct>> byIngredient = new LinkedHashMap<>();
        Map<String, List<InvolvedProduct>> byClass = new LinkedHashMap<>();
        for (ProductIngredients product : products) {
            for (String ingredient : product.ingredients()) {
                InvolvedProduct involved =
                        new InvolvedProduct(product.productId(), product.productName(), ingredient);
                byIngredient.computeIfAbsent(ingredient, key -> new ArrayList<>()).add(involved);
                for (String ingredientClass : ruleService.classesOf(ingredient)) {
                    byClass.computeIfAbsent(ingredientClass, key -> new ArrayList<>()).add(involved);
                }
            }
        }

        List<InteractionWarning> warnings = new ArrayList<>();
        for (InteractionRule rule : ruleService.rules()) {
            List<InvolvedProduct> involved = switch (rule.type()) {
                case CLASS_PAIR -> crossProductPair(byClass.get(rule.a()), byClass.get(rule.b()));
                case INGREDIENT_PAIR ->
                        crossProductPair(byIngredient.get(rule.a()), byIngredient.get(rule.b()));
                case CLASS_INGREDIENT_PAIR ->
                        crossProductPair(byClass.get(rule.a()), byIngredient.get(rule.b()));
                case DUPLICATE_CLASS -> duplicates(byClass.get(rule.a()));
                case DUPLICATE_INGREDIENT -> duplicates(byIngredient.get(rule.a()));
            };
            if (!involved.isEmpty()) {
                warnings.add(new InteractionWarning(rule, involved));
            }
        }
        warnings.sort(Comparator.comparing(warning -> warning.rule().severity()));
        return warnings;
    }

    /**
     * Products from side A and side B, kept only when at least one pair spans
     * two DIFFERENT products.
     */
    private List<InvolvedProduct> crossProductPair(List<InvolvedProduct> sideA, List<InvolvedProduct> sideB) {
        if (sideA == null || sideB == null) {
            return List.of();
        }
        Set<InvolvedProduct> involved = new LinkedHashSet<>();
        for (InvolvedProduct a : sideA) {
            for (InvolvedProduct b : sideB) {
                if (!a.productId().equals(b.productId())) {
                    involved.add(a);
                    involved.add(b);
                }
            }
        }
        return List.copyOf(involved);
    }

    /** Fires when two or more DISTINCT products carry the same ingredient/class. */
    private List<InvolvedProduct> duplicates(List<InvolvedProduct> carriers) {
        if (carriers == null) {
            return List.of();
        }
        Map<Long, InvolvedProduct> byProduct = new LinkedHashMap<>();
        for (InvolvedProduct carrier : carriers) {
            byProduct.putIfAbsent(carrier.productId(), carrier);
        }
        return byProduct.size() < 2 ? List.of() : List.copyOf(byProduct.values());
    }
}
