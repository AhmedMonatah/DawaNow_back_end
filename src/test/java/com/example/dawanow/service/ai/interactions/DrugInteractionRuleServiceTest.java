package com.example.dawanow.service.ai.interactions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Loads the REAL classpath seed files, so this doubles as a lint for the
 * clinical seed data: every rule must reference a class or ingredient that can
 * actually occur, and every rule must carry complete localized copy.
 */
class DrugInteractionRuleServiceTest {

    private DrugInteractionRuleService service;

    @BeforeEach
    void setUp() {
        service = new DrugInteractionRuleService(new ObjectMapper());
        service.load();
    }

    @Test
    void seedFilesLoad() {
        assertThat(service.rules()).isNotEmpty();
        assertThat(service.classesOf("IBUPROFEN")).contains("NSAID");
        assertThat(service.classesOf("ACETYLSALICYLIC ACID")).contains("NSAID", "ANTIPLATELET");
        assertThat(service.classesOf("UNKNOWN THING")).isEmpty();
    }

    @Test
    void everyClassReferencedByARuleHasAtLeastOneIngredient() {
        Set<String> knownClasses = new HashSet<>();
        for (String ingredient : new String[] {
                "IBUPROFEN", "DICLOFENAC", "ACETYLSALICYLIC ACID", "WARFARIN", "RIVAROXABAN",
                "CLOPIDOGREL", "ESCITALOPRAM", "SERTRALINE", "DULOXETINE", "AMITRIPTYLINE",
                "SIMVASTATIN", "CLARITHROMYCIN", "ALLOPURINOL", "BISOPROLOL", "CAPTOPRIL",
                "LOSARTAN", "SPIRONOLACTONE", "FUROSEMIDE", "SILDENAFIL", "TAMSULOSIN",
                "CITALOPRAM", "ALPRAZOLAM", "CHLORPHENIRAMINE", "PIPERAZINE THEOPHYLLINE ETHANOATE"
        }) {
            knownClasses.addAll(service.classesOf(ingredient));
        }

        for (InteractionRule rule : service.rules()) {
            if (rule.type() == InteractionRule.RuleType.CLASS_PAIR
                    || rule.type() == InteractionRule.RuleType.CLASS_INGREDIENT_PAIR
                    || rule.type() == InteractionRule.RuleType.DUPLICATE_CLASS) {
                assertThat(knownClasses)
                        .as("rule class '%s' must exist in the ingredient-class map", rule.a())
                        .contains(rule.a());
            }
        }
    }

    @Test
    void everyRuleHasCompleteLocalizedCopy() {
        for (InteractionRule rule : service.rules()) {
            assertThat(rule.severity()).isNotNull();
            assertThat(rule.titleEn()).isNotBlank();
            assertThat(rule.titleAr()).isNotBlank();
            assertThat(rule.adviceEn()).isNotBlank();
            assertThat(rule.adviceAr()).isNotBlank();
            assertThat(rule.a()).isNotBlank();
            if (rule.type() == InteractionRule.RuleType.CLASS_PAIR
                    || rule.type() == InteractionRule.RuleType.INGREDIENT_PAIR
                    || rule.type() == InteractionRule.RuleType.CLASS_INGREDIENT_PAIR) {
                assertThat(rule.b()).as("pair rule '%s' needs side b", rule.titleEn()).isNotBlank();
            }
        }
    }
}
