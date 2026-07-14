package com.example.dawanow.config;

import com.example.dawanow.entity.Category;
import com.example.dawanow.entity.Product;
import com.example.dawanow.repo.CategoryRepository;
import com.example.dawanow.repo.ProductRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
    private static final String EXPECTED_HEADER =
            "name\tarabicName\tscientificName\tprice\timageUrl\tcategoryName\tcompany\troute";

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (productRepository.count() > 0) {
            log.info("Product import skipped because the product table is not empty");
            return;
        }

        List<ProductSeed> seeds = readSeeds();
        Map<String, Category> categories = loadCategories(seeds);
        List<Product> products = seeds.stream()
                .map(seed -> toProduct(seed, categories.get(seed.categoryName())))
                .toList();

        productRepository.saveAll(products);
        log.info("Imported {} products and {} categories", products.size(), categories.size());
    }

    private List<ProductSeed> readSeeds() throws IOException {
        ClassPathResource dataset = new ClassPathResource(DATASET_PATH);
        List<ProductSeed> seeds = new ArrayList<>();

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
                seeds.add(parseLine(line, lineNumber));
            }
        }

        return seeds;
    }

    private ProductSeed parseLine(String line, int lineNumber) {
        String[] values = line.split("\t", -1);
        if (values.length != 8) {
            throw new IllegalStateException("Invalid product dataset row at line " + lineNumber);
        }

        return new ProductSeed(
                values[0],
                nullable(values[1]),
                nullable(values[2]),
                new BigDecimal(values[3]),
                nullable(values[4]),
                values[5],
                values[6],
                nullable(values[7])
        );
    }

    private Map<String, Category> loadCategories(List<ProductSeed> seeds) {
        Map<String, Category> categories = new HashMap<>();
        categoryRepository.findAll().forEach(category -> categories.put(category.getName(), category));

        Set<String> missingNames = new LinkedHashSet<>();
        for (ProductSeed seed : seeds) {
            if (!categories.containsKey(seed.categoryName())) {
                missingNames.add(seed.categoryName());
            }
        }

        List<Category> missingCategories = missingNames.stream().map(this::newCategory).toList();
        categoryRepository.saveAll(missingCategories)
                .forEach(category -> categories.put(category.getName(), category));
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
        product.setArabicName(seed.arabicName());
        product.setScientificName(seed.scientificName());
        product.setPrice(seed.price());
        product.setImageUrl(seed.imageUrl());
        product.setCategory(category);
        product.setCompany(seed.company());
        product.setRoute(seed.route());
        return product;
    }

    private String nullable(String value) {
        return value.isBlank() ? null : value;
    }

    private record ProductSeed(
            String name,
            String arabicName,
            String scientificName,
            BigDecimal price,
            String imageUrl,
            String categoryName,
            String company,
            String route
    ) {
    }
}
