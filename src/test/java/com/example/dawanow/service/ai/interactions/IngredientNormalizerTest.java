package com.example.dawanow.service.ai.interactions;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IngredientNormalizerTest {

    private final IngredientNormalizer normalizer = new IngredientNormalizer();

    @Test
    void splitsCombinationProducts() {
        assertThat(normalizer.extractIngredients("CAFFEINE+PARACETAMOL(ACETAMINOPHEN)"))
                .containsExactly("CAFFEINE", "PARACETAMOL");
    }

    @Test
    void toleratesSpacesAroundThePlusSign() {
        assertThat(normalizer.extractIngredients("AMLODIPINE + PERINDOPRIL"))
                .containsExactly("AMLODIPINE", "PERINDOPRIL");
    }

    @Test
    void collapsesSaltVariantsToOneIngredient() {
        assertThat(normalizer.extractIngredients("DICLOFENAC POTASSIUM/SODIUM/DIETHYLAMINE"))
                .containsExactly("DICLOFENAC");
        assertThat(normalizer.extractIngredients("BISOPROLOL FUMARATE"))
                .containsExactly("BISOPROLOL");
        assertThat(normalizer.extractIngredients("CANDESARTAN CILEXETIL+HYDROCHLOROTHIAZIDE"))
                .containsExactly("CANDESARTAN", "HYDROCHLOROTHIAZIDE");
    }

    @Test
    void keepsMultiWordActivesIntact() {
        assertThat(normalizer.extractIngredients("VALPROIC ACID")).containsExactly("VALPROIC ACID");
        assertThat(normalizer.extractIngredients("FUSIDIC ACID")).containsExactly("FUSIDIC ACID");
        // The whole name is a salt word, but a single word is never stripped.
        assertThat(normalizer.extractIngredients("SODIUM CHLORIDE")).containsExactly("SODIUM CHLORIDE");
    }

    @Test
    void deduplicatesAndSkipsBlanks() {
        assertThat(normalizer.extractIngredients("PARACETAMOL(ACETAMINOPHEN)+PARACETAMOL"))
                .containsExactly("PARACETAMOL");
        assertThat(normalizer.extractIngredients(null)).isEmpty();
        assertThat(normalizer.extractIngredients("  ")).isEmpty();
    }
}
