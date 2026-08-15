package com.example.dawanow.service.ai.interactions;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Loads the drug-interaction seed data from the classpath.
 *
 * <p>Fails soft by design: a malformed seed file logs an error and leaves the
 * rule set empty, which degrades the feature to "no warnings" instead of
 * breaking the cart or the chat.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DrugInteractionRuleService {

    private static final String CLASSES_RESOURCE = "data/drug_ingredient_classes.tsv";
    private static final String RULES_RESOURCE = "data/drug_interaction_rules.json";

    private final ObjectMapper objectMapper;

    private Map<String, Set<String>> classesByIngredient = Map.of();
    private List<InteractionRule> rules = List.of();

    @PostConstruct
    void load() {
        try {
            classesByIngredient = loadClasses();
            rules = loadRules();
            log.info("Loaded {} interaction rules over {} classified ingredients",
                    rules.size(), classesByIngredient.size());
        } catch (Exception exception) {
            classesByIngredient = Map.of();
            rules = List.of();
            log.error("Drug interaction seed data failed to load; warnings are disabled", exception);
        }
    }

    /** Classes for a normalized ingredient name; empty when unclassified. */
    public Set<String> classesOf(String ingredient) {
        return classesByIngredient.getOrDefault(ingredient.toUpperCase(Locale.ROOT), Set.of());
    }

    public List<InteractionRule> rules() {
        return rules;
    }

    private Map<String, Set<String>> loadClasses() throws IOException {
        Map<String, Set<String>> classes = new HashMap<>();
        try (InputStream stream = new ClassPathResource(CLASSES_RESOURCE).getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                String[] columns = trimmed.split("\t");
                if (columns.length < 2) {
                    log.warn("Skipping malformed ingredient-class row: {}", trimmed);
                    continue;
                }
                classes.computeIfAbsent(columns[0].strip().toUpperCase(Locale.ROOT), key -> new TreeSet<>())
                        .add(columns[1].strip().toUpperCase(Locale.ROOT));
            }
        }
        return Map.copyOf(classes);
    }

    private List<InteractionRule> loadRules() throws IOException {
        try (InputStream stream = new ClassPathResource(RULES_RESOURCE).getInputStream()) {
            InteractionRule[] loaded = objectMapper.readValue(stream, InteractionRule[].class);
            return List.of(loaded);
        }
    }
}
