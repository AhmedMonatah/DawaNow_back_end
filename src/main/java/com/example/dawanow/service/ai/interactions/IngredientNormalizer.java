package com.example.dawanow.service.ai.interactions;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Turns a product's {@code scientificName} into canonical ingredient names.
 *
 * <p>The catalog stores combination products joined by {@code +} (sometimes with
 * spaces), synonym parentheticals like {@code PARACETAMOL(ACETAMINOPHEN)}, salt
 * variants like {@code DICLOFENAC POTASSIUM/SODIUM/DIETHYLAMINE}, and trailing
 * salt or ester words. All of those must collapse to the same ingredient before
 * interaction rules can match.</p>
 */
@Component
public class IngredientNormalizer {

    /**
     * Trailing words that name a salt or ester, not the active itself. Only
     * stripped while more than one word remains, which protects names like
     * VALPROIC ACID or SODIUM CHLORIDE from being emptied.
     */
    private static final Set<String> SALT_WORDS = Set.of(
            "SODIUM", "POTASSIUM", "CALCIUM", "MAGNESIUM",
            "HYDROCHLORIDE", "HCL", "DIGLUCONATE",
            "SULFATE", "SULPHATE", "MALEATE", "FUMARATE", "TARTRATE", "CITRATE",
            "DIETHYLAMINE", "MESYLATE", "BESYLATE", "SUCCINATE", "ACETATE",
            "PHOSPHATE", "NITRATE", "TROMETHAMINE", "CILEXETIL", "MEDOXOMIL",
            "MONOHYDRATE", "TRIHYDRATE", "ANHYDROUS"
    );

    public List<String> extractIngredients(String scientificName) {
        if (scientificName == null || scientificName.isBlank()) {
            return List.of();
        }

        Set<String> ingredients = new LinkedHashSet<>();
        for (String token : scientificName.toUpperCase(Locale.ROOT).split("\\s*\\+\\s*")) {
            String normalized = normalizeToken(token);
            if (!normalized.isBlank()) {
                ingredients.add(normalized);
            }
        }
        return List.copyOf(ingredients);
    }

    private String normalizeToken(String token) {
        String cleaned = token.trim();

        // "PARACETAMOL(ACETAMINOPHEN)" -> "PARACETAMOL"
        int parenthesis = cleaned.indexOf('(');
        if (parenthesis > 0) {
            cleaned = cleaned.substring(0, parenthesis);
        }

        // "DICLOFENAC POTASSIUM/SODIUM/DIETHYLAMINE" -> "DICLOFENAC POTASSIUM"
        int slash = cleaned.indexOf('/');
        if (slash > 0) {
            cleaned = cleaned.substring(0, slash);
        }

        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        List<String> words = new ArrayList<>(List.of(cleaned.split(" ")));
        while (words.size() > 1 && SALT_WORDS.contains(words.getLast())) {
            words.removeLast();
        }
        return String.join(" ", words).trim();
    }
}
