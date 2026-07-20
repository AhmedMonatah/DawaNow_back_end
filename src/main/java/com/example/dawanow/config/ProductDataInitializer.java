package com.example.dawanow.config;

import com.example.dawanow.entity.Category;
import com.example.dawanow.entity.CategoryTranslation;
import com.example.dawanow.entity.Product;
import com.example.dawanow.entity.ProductTranslation;
import com.example.dawanow.repo.CategoryRepository;
import com.example.dawanow.repo.CategoryTranslationRepository;
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
            "product_name\tstrength\tpack_size\tform\tprice\tscientificName\t"
                    + "scientific_category\tconsumer_category\tcompany\troute\tdescription\timageUrl";
    private static final String EXPECTED_TRANSLATION_HEADER =
            "price\tname\tproduct_name\tstrength\tpack_size\tform\tscientificName\t"
                    + "scientific_category\tconsumer_category\tcompany\troute\tdescription\timageUrl";
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
    private final CategoryTranslationRepository categoryTranslationRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        List<ProductSeed> seeds = readSeeds();
        Map<ProductKey, ProductTranslationSeed> translationSeeds = readTranslationSeeds(seeds);
        Map<ProductKey, Product> products;

        if (productRepository.count() == 0) {
            Map<String, Category> categories = loadCategories(seeds);
            List<Product> importedProducts = seeds.stream()
                    .map(seed -> toProduct(
                            seed,
                            categories.get(categoryKey(seed.consumerCategory()))
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
        synchronizeArabicCategoryTranslations(seeds, translationSeeds, products);
    }

    private List<ProductSeed> readSeeds() throws IOException {
        ClassPathResource dataset = new ClassPathResource(DATASET_PATH);
        List<ProductSeed> seeds = new ArrayList<>();
        Set<ProductKey> productKeys = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(dataset.getInputStream(), StandardCharsets.UTF_8))) {
            String header = readHeader(reader);
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

    private Map<ProductKey, ProductTranslationSeed> readTranslationSeeds(
            List<ProductSeed> productSeeds
    ) throws IOException {
        ClassPathResource dataset = new ClassPathResource(ARABIC_TRANSLATIONS_PATH);
        Map<ProductKey, ProductTranslationSeed> seeds = new HashMap<>();
        int productIndex = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(dataset.getInputStream(), StandardCharsets.UTF_8))) {
            String header = readHeader(reader);
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
                if (productIndex >= productSeeds.size()) {
                    throw new IllegalStateException(
                            "Arabic product translation dataset has more rows than the product dataset"
                    );
                }

                ProductSeed productSeed = productSeeds.get(productIndex++);
                validateTranslationPair(productSeed, seed, lineNumber);
                ProductKey key = productKey(productSeed.name(), productSeed.price());
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
        if (productIndex != productSeeds.size()) {
            throw new IllegalStateException(
                    "Arabic product translation dataset has fewer rows than the product dataset"
            );
        }
        return seeds;
    }

    private String readHeader(BufferedReader reader) throws IOException {
        String header = reader.readLine();
        if (header != null && header.startsWith("\uFEFF")) {
            return header.substring(1);
        }
        return header;
    }

    private ProductSeed parseLine(String line, int lineNumber) {
        String[] values = line.split("\t", -1);
        if (values.length != 12) {
            throw new IllegalStateException("Invalid product dataset row at line " + lineNumber);
        }

        String productName = required(values[0], "product_name", 500, lineNumber);
        String strength = optional(values[1], "strength", 100, lineNumber);
        String packSize = optional(values[2], "pack_size", 100, lineNumber);
        String form = required(values[3], "form", 100, lineNumber);
        BigDecimal price = parsePrice(values[4], lineNumber);
        String scientificName = required(values[5], "scientificName", 1000, lineNumber);
        String scientificCategory = required(values[6], "scientific_category", 255, lineNumber);
        String consumerCategory = required(values[7], "consumer_category", 255, lineNumber);
        String company = required(values[8], "company", 500, lineNumber);
        String route = required(values[9], "route", 100, lineNumber);
        if (!VALID_ROUTES.contains(route)) {
            throw invalidField("route", lineNumber);
        }
        String description = required(values[10], "description", 2000, lineNumber);
        String imageUrl = required(values[11], "imageUrl", 1000, lineNumber);
        validateImageUrl(imageUrl, lineNumber);
        String name = buildDisplayName(productName, strength, packSize, form, lineNumber);

        return new ProductSeed(
                name,
                productName,
                strength,
                packSize,
                form,
                price,
                scientificName,
                scientificCategory,
                consumerCategory,
                company,
                route,
                description,
                imageUrl
        );
    }

    private ProductTranslationSeed parseTranslationLine(String line, int lineNumber) {
        String[] values = line.split("\t", -1);
        if (values.length != 13) {
            throw new IllegalStateException(
                    "Invalid Arabic product translation row at line " + lineNumber
            );
        }

        BigDecimal price = parsePrice(values[0], lineNumber);
        String imageUrl = required(values[12], "translated imageUrl", 1000, lineNumber);
        validateImageUrl(imageUrl, lineNumber);
        return new ProductTranslationSeed(
                price,
                required(values[1], "translated name", 500, lineNumber),
                required(values[2], "translated product_name", 500, lineNumber),
                optional(values[3], "translated strength", 100, lineNumber),
                optional(values[4], "translated pack_size", 100, lineNumber),
                required(values[5], "translated form", 100, lineNumber),
                required(values[6], "translated scientificName", 1000, lineNumber),
                required(values[7], "translated scientific_category", 255, lineNumber),
                required(values[8], "translated consumer_category", 255, lineNumber),
                required(values[9], "translated company", 500, lineNumber),
                required(values[10], "translated route", 100, lineNumber),
                required(values[11], "translated description", 2000, lineNumber),
                imageUrl
        );
    }

    private void validateTranslationPair(
            ProductSeed productSeed,
            ProductTranslationSeed translationSeed,
            int lineNumber
    ) {
        if (productSeed.price().compareTo(translationSeed.price()) != 0
                || !productSeed.imageUrl().equals(translationSeed.imageUrl())) {
            throw new IllegalStateException(
                    "Arabic product translation does not match the product dataset at line " + lineNumber
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
            if (!categories.containsKey(categoryKey(seed.consumerCategory()))) {
                missingNames.add(seed.consumerCategory());
            }
        }

        List<Category> missingCategories = missingNames.stream().map(this::newCategory).toList();
        categoryRepository.saveAll(missingCategories)
                .forEach(category -> categories.put(categoryKey(category.getName()), category));
        return categories;
    }

    private Category newCategory(String name) {
        Category category = new Category();
        category.setName(name.trim().toUpperCase(Locale.ROOT));
        return category;
    }

    private Product toProduct(ProductSeed seed, Category category) {
        Product product = new Product();
        product.setName(seed.name());
        product.setProductName(seed.productName());
        product.setStrength(seed.strength());
        product.setPackSize(seed.packSize());
        product.setForm(seed.form());
        product.setScientificName(seed.scientificName());
        product.setScientificCategory(seed.scientificCategory());
        product.setPrice(seed.price());
        product.setImageUrl(seed.imageUrl());
        product.setCategory(category);
        product.setCompany(seed.company());
        product.setRoute(seed.route());
        product.setDescription(seed.description());
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
            translation.setProductName(seed.productName());
            translation.setStrength(seed.strength());
            translation.setPackSize(seed.packSize());
            translation.setForm(seed.form());
            translation.setScientificName(seed.scientificName());
            translation.setScientificCategory(seed.scientificCategory());
            translation.setConsumerCategory(seed.consumerCategory());
            translation.setCompany(seed.company());
            translation.setRoute(seed.route());
            translation.setDescription(seed.description());
            translations.add(translation);
        }

        productTranslationRepository.saveAll(translations);
        log.info("Synchronized {} Arabic product translations", translations.size());
    }

    private void synchronizeArabicCategoryTranslations(
            List<ProductSeed> productSeeds,
            Map<ProductKey, ProductTranslationSeed> translationSeeds,
            Map<ProductKey, Product> products
    ) {
        Map<Long, Category> categories = new HashMap<>();
        Map<Long, String> translatedNames = new HashMap<>();
        for (ProductSeed productSeed : productSeeds) {
            ProductKey key = productKey(productSeed.name(), productSeed.price());
            Category category = products.get(key).getCategory();
            String translatedName = translationSeeds.get(key).consumerCategory();
            categories.put(category.getId(), category);

            String existingName = translatedNames.putIfAbsent(category.getId(), translatedName);
            if (existingName != null && !existingName.equals(translatedName)) {
                throw new IllegalStateException(
                        "Conflicting Arabic translations for category: " + category.getName()
                );
            }
        }

        Map<Long, CategoryTranslation> existingTranslations = new HashMap<>();
        for (CategoryTranslation translation : categoryTranslationRepository.findAllByLang(ARABIC)) {
            Long categoryId = translation.getCategory().getId();
            if (existingTranslations.putIfAbsent(categoryId, translation) != null) {
                throw new IllegalStateException(
                        "Duplicate Arabic translation for category ID " + categoryId
                );
            }
        }

        List<CategoryTranslation> translations = new ArrayList<>();
        translatedNames.forEach((categoryId, translatedName) -> {
            CategoryTranslation translation = existingTranslations.get(categoryId);
            if (translation == null) {
                translation = new CategoryTranslation();
                translation.setCategory(categories.get(categoryId));
                translation.setLang(ARABIC);
            }
            translation.setName(translatedName);
            translations.add(translation);
        });

        categoryTranslationRepository.saveAll(translations);
        log.info("Synchronized {} Arabic category translations", translations.size());
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

    private String optional(String value, String fieldName, int maxLength, int lineNumber) {
        String normalized = value.trim();
        if (normalized.isEmpty() || NULL_LIKE_VALUES.contains(normalized.toUpperCase(Locale.ROOT))) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw invalidField(fieldName, lineNumber);
        }
        return normalized;
    }

    private String buildDisplayName(
            String productName,
            String strength,
            String packSize,
            String form,
            int lineNumber
    ) {
        String name = java.util.stream.Stream.of(productName, strength, packSize, form)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.joining(" "));
        if (name.length() > 500) {
            throw invalidField("name", lineNumber);
        }
        return name;
    }

    private BigDecimal parsePrice(String value, int lineNumber) {
        try {
            BigDecimal price = new BigDecimal(value.trim());
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
            String productName,
            String strength,
            String packSize,
            String form,
            BigDecimal price,
            String scientificName,
            String scientificCategory,
            String consumerCategory,
            String company,
            String route,
            String description,
            String imageUrl
    ) {
    }

    private record ProductTranslationSeed(
            BigDecimal price,
            String name,
            String productName,
            String strength,
            String packSize,
            String form,
            String scientificName,
            String scientificCategory,
            String consumerCategory,
            String company,
            String route,
            String description,
            String imageUrl
    ) {
    }
}
