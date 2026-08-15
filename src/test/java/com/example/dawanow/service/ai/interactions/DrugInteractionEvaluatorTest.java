package com.example.dawanow.service.ai.interactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.dawanow.service.ai.interactions.DrugInteractionEvaluator.InteractionWarning;
import com.example.dawanow.service.ai.interactions.DrugInteractionEvaluator.ProductIngredients;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DrugInteractionEvaluatorTest {

    private DrugInteractionRuleService ruleService;
    private DrugInteractionEvaluator evaluator;

    private static final InteractionRule ANTICOAGULANT_NSAID = new InteractionRule(
            InteractionRule.RuleType.CLASS_PAIR, "ANTICOAGULANT", "NSAID",
            InteractionRule.Severity.HIGH, "Bleeding risk", "خطر نزيف", "Ask a doctor", "استشر طبيبك");
    private static final InteractionRule PARACETAMOL_DUP = new InteractionRule(
            InteractionRule.RuleType.DUPLICATE_INGREDIENT, "PARACETAMOL", null,
            InteractionRule.Severity.MODERATE, "Double paracetamol", "باراسيتامول مزدوج", "Keep one", "احتفظ بواحد");

    @BeforeEach
    void setUp() {
        ruleService = mock(DrugInteractionRuleService.class);
        evaluator = new DrugInteractionEvaluator(ruleService);
        when(ruleService.classesOf("WARFARIN")).thenReturn(Set.of("ANTICOAGULANT"));
        when(ruleService.classesOf("IBUPROFEN")).thenReturn(Set.of("NSAID"));
        when(ruleService.classesOf("PARACETAMOL")).thenReturn(Set.of());
        when(ruleService.classesOf("CAFFEINE")).thenReturn(Set.of());
        when(ruleService.rules()).thenReturn(List.of(ANTICOAGULANT_NSAID, PARACETAMOL_DUP));
    }

    @Test
    void classPairFiresAcrossDifferentProducts() {
        List<InteractionWarning> warnings = evaluator.evaluate(List.of(
                new ProductIngredients(1L, "MAREVAN", List.of("WARFARIN")),
                new ProductIngredients(2L, "BRUFEN", List.of("IBUPROFEN"))
        ));

        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst().rule().titleEn()).isEqualTo("Bleeding risk");
        assertThat(warnings.getFirst().products())
                .extracting(DrugInteractionEvaluator.InvolvedProduct::productId)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void fixedCombinationInsideOneProductNeverWarnsAgainstItself() {
        List<InteractionWarning> warnings = evaluator.evaluate(List.of(
                new ProductIngredients(1L, "PANADOL EXTRA", List.of("CAFFEINE", "PARACETAMOL")),
                new ProductIngredients(2L, "MAREVAN", List.of("WARFARIN"))
        ));

        assertThat(warnings).isEmpty();
    }

    @Test
    void duplicateIngredientFiresOnlyAcrossTwoProducts() {
        List<InteractionWarning> warnings = evaluator.evaluate(List.of(
                new ProductIngredients(1L, "PANADOL EXTRA", List.of("CAFFEINE", "PARACETAMOL")),
                new ProductIngredients(2L, "ABIMOL", List.of("PARACETAMOL"))
        ));

        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst().rule().titleEn()).isEqualTo("Double paracetamol");
        assertThat(warnings.getFirst().products()).hasSize(2);
    }

    @Test
    void highSeverityWarningsComeFirst() {
        List<InteractionWarning> warnings = evaluator.evaluate(List.of(
                new ProductIngredients(1L, "MAREVAN", List.of("WARFARIN")),
                new ProductIngredients(2L, "BRUFEN", List.of("IBUPROFEN")),
                new ProductIngredients(3L, "ABIMOL", List.of("PARACETAMOL")),
                new ProductIngredients(4L, "PANADOL", List.of("PARACETAMOL"))
        ));

        assertThat(warnings).hasSize(2);
        assertThat(warnings.getFirst().rule().severity()).isEqualTo(InteractionRule.Severity.HIGH);
    }

    @Test
    void singleProductCartHasNoWarnings() {
        assertThat(evaluator.evaluate(List.of(
                new ProductIngredients(1L, "MAREVAN", List.of("WARFARIN"))
        ))).isEmpty();
    }
}
