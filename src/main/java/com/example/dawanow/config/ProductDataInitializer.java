package com.example.dawanow.config;

import com.example.dawanow.entity.Category;
import com.example.dawanow.entity.Product;
import com.example.dawanow.entity.ProductTranslation;
import com.example.dawanow.repo.CategoryRepository;
import com.example.dawanow.repo.ProductRepository;
import com.example.dawanow.repo.ProductTranslationRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "dawanow.data.products.import-enabled", havingValue = "true")
public class ProductDataInitializer implements ApplicationRunner {

    private static final String DATASET_PATH = "data/products.tsv";
    private static final String ARABIC_TRANSLATIONS_PATH = "data/product_translations_ar.tsv";
    private static final String EXPECTED_HEADER =
            "name\tscientificName\tprice\timageUrl\tcategoryName\tcompany\troute";
    private static final String EXPECTED_TRANSLATION_HEADER =
            "productName\tprice\tname\tscientificName\tcategoryName\tcompany\troute";
    private static final String ARABIC = "ar";
    private static final Set<String> VALID_ROUTES = Set.of(
            "EAR",
            "EFF",
            "EYE",
            "INJECTION",
            "MOUTH",
            "ORAL.LIQUID",
            "ORAL.SOLID",
            "RECTAL",
            "SPRAY",
            "TOPICAL"
    );
    private static final Set<String> NULL_LIKE_VALUES = Set.of(".", "N/A", "NA", "NONE", "NULL", "UNKNOWN");

    private final ProductRepository productRepository;
    private final ProductTranslationRepository productTranslationRepository;
    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        List<ProductSeed> seeds = readSeeds();
        Map<ProductKey, ProductTranslationSeed> translationSeeds = readTranslationSeeds();
        validateTranslationCoverage(seeds, translationSeeds);
        Map<ProductKey, Product> products;

        if (productRepository.count() == 0) {
            Map<String, Category> categories = loadCategories(seeds);
            List<Product> importedProducts = seeds.stream()
                    .map(seed -> toProduct(
                            seed,
                            categories.get(categoryKey(seed.categoryName()))
                    ))
                    .toList();

            productRepository.saveAll(importedProducts);
            products = indexProducts(importedProducts);
            log.info("Imported {} products and {} categories", importedProducts.size(), categories.size());
        } else {
            products = indexProducts(productRepository.findAll());
            for (ProductSeed seed : seeds) {
                ProductKey key = productKey(seed.name(), seed.price());
                Product product = products.get(key);
                if (product == null) {
                    throw new IllegalStateException(
                            "Existing product table does not contain dataset product: " + seed.name()
                    );
                }
            }
            log.info("Product import skipped because the product table is not empty");
        }

        synchronizeArabicTranslations(seeds, translationSeeds, products);
    }

    private List<ProductSeed> readSeeds() throws IOException {
        ClassPathResource dataset = new ClassPathResource(DATASET_PATH);
        List<ProductSeed> seeds = new ArrayList<>();
        Set<ProductKey> productKeys = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(dataset.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (!EXPECTED_HEADER.equals(header)) {
                throw new IllegalStateException("Unexpected product dataset header");
            }

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                ProductSeed seed = parseLine(line, lineNumber);
                ProductKey key = productKey(seed.name(), seed.price());
                if (!productKeys.add(key)) {
                    throw new IllegalStateException("Duplicate product dataset row at line " + lineNumber);
                }
                seeds.add(seed);
            }
        }

        if (seeds.isEmpty()) {
            throw new IllegalStateException("Product dataset is empty");
        }
        return seeds;
    }

    private Map<ProductKey, ProductTranslationSeed> readTranslationSeeds() throws IOException {
        ClassPathResource dataset = new ClassPathResource(ARABIC_TRANSLATIONS_PATH);
        Map<ProductKey, ProductTranslationSeed> seeds = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(dataset.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (!EXPECTED_TRANSLATION_HEADER.equals(header)) {
                throw new IllegalStateException("Unexpected Arabic product translation dataset header");
            }

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }

                ProductTranslationSeed seed = parseTranslationLine(line, lineNumber);
                ProductKey key = productKey(seed.productName(), seed.price());
                if (seeds.putIfAbsent(key, seed) != null) {
                    throw new IllegalStateException(
                            "Duplicate Arabic product translation at line " + lineNumber
                    );
                }
            }
        }

        if (seeds.isEmpty()) {
            throw new IllegalStateException("Arabic product translation dataset is empty");
        }
        return seeds;
    }

    private ProductSeed parseLine(String line, int lineNumber) {
        String[] values = line.split("\t", -1);
        if (values.length != 7) {
            throw new IllegalStateException("Invalid product dataset row at line " + lineNumber);
        }

        String name = required(values[0], "name", 500, lineNumber);
        String scientificName = required(values[1], "scientificName", 1000, lineNumber);
        BigDecimal price = parsePrice(values[2], lineNumber);
        String imageUrl = required(values[3], "imageUrl", 1000, lineNumber);
        validateImageUrl(imageUrl, lineNumber);
        String categoryName = required(values[4], "categoryName", 255, lineNumber);
        String company = required(values[5], "company", 500, lineNumber);
        String route = required(values[6], "route", 100, lineNumber);
        if (!VALID_ROUTES.contains(route)) {
            throw invalidField("route", lineNumber);
        }

        return new ProductSeed(
                name,
                scientificName,
                price,
                imageUrl,
                categoryName,
                company,
                route
        );
    }

    private ProductTranslationSeed parseTranslationLine(String line, int lineNumber) {
        String[] values = line.split("\t", -1);
        if (values.length != 7) {
            throw new IllegalStateException(
                    "Invalid Arabic product translation row at line " + lineNumber
            );
        }

        return new ProductTranslationSeed(
                required(values[0], "productName", 500, lineNumber),
                parsePrice(values[1], lineNumber),
                required(values[2], "translated name", 500, lineNumber),
                required(values[3], "translated scientificName", 1000, lineNumber),
                required(values[4], "translated categoryName", 255, lineNumber),
                required(values[5], "translated company", 500, lineNumber),
                required(values[6], "translated route", 100, lineNumber)
        );
    }

    private void validateTranslationCoverage(
            List<ProductSeed> productSeeds,
            Map<ProductKey, ProductTranslationSeed> translationSeeds
    ) {
        Set<ProductKey> productKeys = productSeeds.stream()
                .map(seed -> productKey(seed.name(), seed.price()))
                .collect(java.util.stream.Collectors.toSet());

        if (!productKeys.equals(translationSeeds.keySet())) {
            throw new IllegalStateException(
                    "Arabic translations must match every product dataset row exactly"
            );
        }
    }

    private Map<String, Category> loadCategories(List<ProductSeed> seeds) {
        Map<String, Category> categories = new HashMap<>();
        categoryRepository.findAll().forEach(category -> {
            Category duplicate = categories.putIfAbsent(categoryKey(category.getName()), category);
            if (duplicate != null) {
                throw new IllegalStateException("Duplicate category name in database: " + category.getName());
            }
        });

        Set<String> missingNames = new LinkedHashSet<>();
        for (ProductSeed seed : seeds) {
            if (!categories.containsKey(categoryKey(seed.categoryName()))) {
                missingNames.add(seed.categoryName());
            }
        }

        List<Category> missingCategories = missingNames.stream().map(this::newCategory).toList();
        categoryRepository.saveAll(missingCategories)
                .forEach(category -> categories.put(categoryKey(category.getName()), category));
        return categories;
    }

    private Category newCategory(String name) {
        Category category = new Category();
        category.setName(name);
        return category;
    }

    private Product toProduct(ProductSeed seed, Category category) {
        Product product = new Product();
        product.setName(seed.name());
        product.setScientificName(seed.scientificName());
        product.setPrice(seed.price());
        product.setImageUrl(seed.imageUrl());
        product.setCategory(category);
        product.setCompany(seed.company());
        product.setRoute(seed.route());
        return product;
    }

    private Map<ProductKey, Product> indexProducts(List<Product> products) {
        Map<ProductKey, Product> indexedProducts = new HashMap<>();
        for (Product product : products) {
            ProductKey key = productKey(product.getName(), product.getPrice());
            if (indexedProducts.putIfAbsent(key, product) != null) {
                throw new IllegalStateException(
                        "Duplicate product name and price in database: " + product.getName()
                );
            }
        }
        return indexedProducts;
    }

    private void synchronizeArabicTranslations(
            List<ProductSeed> productSeeds,
            Map<ProductKey, ProductTranslationSeed> translationSeeds,
            Map<ProductKey, Product> products
    ) {
        Map<Long, ProductTranslation> existingTranslations = new HashMap<>();
        for (ProductTranslation translation : productTranslationRepository.findAllByLang(ARABIC)) {
            if (existingTranslations.putIfAbsent(translation.getProduct().getId(), translation) != null) {
                throw new IllegalStateException(
                        "Duplicate Arabic translation for product ID " + translation.getProduct().getId()
                );
            }
        }

        List<ProductTranslation> translations = new ArrayList<>();
        for (ProductSeed productSeed : productSeeds) {
            ProductKey key = productKey(productSeed.name(), productSeed.price());
            Product product = products.get(key);
            ProductTranslationSeed seed = translationSeeds.get(key);
            ProductTranslation translation = existingTranslations.get(product.getId());
            if (translation == null) {
                translation = new ProductTranslation();
                translation.setProduct(product);
                translation.setLang(ARABIC);
            }

            translation.setName(seed.name());
            translation.setScientificName(seed.scientificName());
            translation.setCategoryName(seed.categoryName());
            translation.setCompany(seed.company());
            translation.setRoute(seed.route());
            translations.add(translation);
        }

        productTranslationRepository.saveAll(translations);
        log.info("Synchronized {} Arabic product translations", translations.size());
    }

    private String required(String value, String fieldName, int maxLength, int lineNumber) {
        String normalized = value.trim();
        if (normalized.isEmpty()
                || normalized.length() > maxLength
                || NULL_LIKE_VALUES.contains(normalized.toUpperCase(Locale.ROOT))) {
            throw invalidField(fieldName, lineNumber);
        }
        return normalized;
    }

    private BigDecimal parsePrice(String value, int lineNumber) {
        try {
            BigDecimal price = new BigDecimal(value);
            if (price.signum() <= 0 || price.scale() > 2 || price.precision() > 12) {
                throw invalidField("price", lineNumber);
            }
            return price;
        } catch (NumberFormatException exception) {
            throw invalidField("price", lineNumber);
        }
    }

    private void validateImageUrl(String value, int lineNumber) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw invalidField("imageUrl", lineNumber);
            }
        } catch (IllegalArgumentException exception) {
            throw invalidField("imageUrl", lineNumber);
        }
    }

    private String categoryKey(String name) {
        return name.trim().toUpperCase(Locale.ROOT);
    }

    private ProductKey productKey(String name, BigDecimal price) {
        return new ProductKey(
                name.trim().toUpperCase(Locale.ROOT),
                price.stripTrailingZeros()
        );
    }

    private IllegalStateException invalidField(String fieldName, int lineNumber) {
        return new IllegalStateException(
                "Invalid product dataset field '" + fieldName + "' at line " + lineNumber
        );
    }

    private record ProductKey(String name, BigDecimal price) {
    }

    private record ProductSeed(
            String name,
            String scientificName,
            BigDecimal price,
            String imageUrl,
            String categoryName,
            String company,
            String route
    ) {
    }

    private record ProductTranslationSeed(
            String productName,
            BigDecimal price,
            String name,
            String scientificName,
            String categoryName,
            String company,
            String route
    ) {
    }
}
