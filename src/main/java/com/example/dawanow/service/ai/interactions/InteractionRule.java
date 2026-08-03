package com.example.dawanow.service.ai.interactions;

/**
 * One deterministic drug-interaction rule loaded from
 * {@code resources/data/drug_interaction_rules.json}.
 *
 * <p>{@code a} and {@code b} name an ingredient class or a specific ingredient
 * depending on {@code type}; the {@code DUPLICATE_*} types use only {@code a}.
 * All warning copy lives here as data so clinical corrections never require a
 * code change.</p>
 */
public record InteractionRule(
        RuleType type,
        String a,
        String b,
        Severity severity,
        String titleEn,
        String titleAr,
        String adviceEn,
        String adviceAr
) {

    public enum RuleType {
        CLASS_PAIR,
        INGREDIENT_PAIR,
        CLASS_INGREDIENT_PAIR,
        DUPLICATE_CLASS,
        DUPLICATE_INGREDIENT
    }

    public enum Severity {
        HIGH,
        MODERATE
    }

    public String title(String language) {
        return "ar".equals(language) ? titleAr : titleEn;
    }

    public String advice(String language) {
        return "ar".equals(language) ? adviceAr : adviceEn;
    }
}
