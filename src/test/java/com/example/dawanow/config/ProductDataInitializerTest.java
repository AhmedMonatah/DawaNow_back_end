package com.example.dawanow.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dawanow.dtos.request.CreateCategoryRequest;
import com.example.dawanow.dtos.request.UpdateCategoryRequest;
import com.example.dawanow.dtos.response.CategoryResponse;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.dtos.response.ProductResponse;
import com.example.dawanow.entity.Category;
import com.example.dawanow.entity.Product;
import com.example.dawanow.entity.ProductTranslation;
import com.example.dawanow.repo.CategoryRepository;
import com.example.dawanow.repo.ProductRepository;
import com.example.dawanow.repo.ProductTranslationRepository;
import com.example.dawanow.service.CategoryService;
import com.example.dawanow.service.ProductService;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "dawanow.data.products.import-enabled=true")
class ProductDataInitializerTest {

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
    private static final Pattern NON_MEDICINE_CATEGORY = Pattern.compile(
            "MILK PRODUCTS?|INFANT FORMULA|BABY FOOD|COSMETIC|SKIN CARE|HAIR CARE|"
                    + "BODY CARE|PERSONAL CARE|SUPPLEMENT|NUTRITION|DRINKS|SWEETENER|PURIFIED WATER"
    );
    private static final Pattern ARABIC_TEXT = Pattern.compile("[\\u0600-\\u06FF]");
    private static final String BROWSER_TABS_TRANSLATION =
            "\u0639\u0644\u0627\u0645\u0627\u062a \u0627\u0644\u062a\u0628\u0648\u064a\u0628";
    private static final String UNPAID_BILL_TRANSLATION =
            "\u0641\u0627\u062a\u0648\u0631\u0629 \u063a\u064a\u0631 \u0645\u062f\u0641\u0648\u0639\u0629";

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductTranslationRepository productTranslationRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductDataInitializer productDataInitializer;

    @Test
    @Transactional
    void importsProductDataset() {
        List<Product> products = productRepository.findAll();
        List<Category> categories = categoryRepository.findAll();
        List<ProductTranslation> translations = productTranslationRepository.findAllByLang("ar");

        assertThat(products).hasSize(798);
        assertThat(categories).hasSize(226);
        assertThat(translations).hasSize(798);
        assertThat(translations)
                .extracting(ProductTranslation::getName)
                .noneMatch(name -> name.contains(BROWSER_TABS_TRANSLATION))
                .noneMatch(name -> name.contains(UNPAID_BILL_TRANSLATION));
        assertThat(categories)
                .extracting(Category::getName)
                .doesNotHaveDuplicates()
                .allMatch(name -> !name.isBlank() && name.equals(name.toUpperCase()))
                .noneMatch(name -> NON_MEDICINE_CATEGORY.matcher(name).find());
        assertThat(products)
                .extracting(Product::getName, Product::getPrice)
                .doesNotHaveDuplicates();
        assertThat(products.stream()
                .map(product -> product.getCategory().getId())
                .collect(Collectors.toSet()))
                .hasSize(categories.size());
        assertThat(products).allSatisfy(product -> {
            assertThat(product.getName()).isNotBlank();
            assertThat(product.getScientificName()).isNotBlank();
            assertThat(product.getPrice()).isPositive();
            assertThat(product.getImageUrl()).startsWith("https://");
            assertThat(product.getCategory()).isNotNull();
            assertThat(product.getCategory().getId()).isNotNull();
            assertThat(product.getCompany()).isNotBlank();
            assertThat(product.getRoute()).isIn(VALID_ROUTES);
        });
        assertThat(translations).allSatisfy(translation -> {
            assertThat(translation.getProduct()).isNotNull();
            assertThat(translation.getLang()).isEqualTo("ar");
            assertThat(translation.getName()).containsPattern(ARABIC_TEXT);
            assertThat(translation.getScientificName()).containsPattern(ARABIC_TEXT);
            assertThat(translation.getCategoryName()).containsPattern(ARABIC_TEXT);
            assertThat(translation.getCompany()).containsPattern(ARABIC_TEXT);
            assertThat(translation.getRoute()).containsPattern(ARABIC_TEXT);
        });

        PaginatedResponse<ProductResponse> arabicProducts = productService.getAllProducts(
                "ar",
                PageRequest.of(0, 20, Sort.by("name"))
        );
        assertThat(arabicProducts.totalElements()).isEqualTo(798);
        assertThat(arabicProducts.content()).allSatisfy(product -> {
            assertThat(product.name()).containsPattern(ARABIC_TEXT);
            assertThat(product.scientificName()).containsPattern(ARABIC_TEXT);
            assertThat(product.categoryName()).containsPattern(ARABIC_TEXT);
            assertThat(product.company()).containsPattern(ARABIC_TEXT);
            assertThat(product.route()).containsPattern(ARABIC_TEXT);
        });

        ProductResponse arabicProduct = arabicProducts.content().getFirst();
        ProductResponse englishProduct = productService.getProductById(arabicProduct.id(), "en");
        assertThat(arabicProduct.name()).isNotEqualTo(englishProduct.name());
        assertThat(arabicProduct.scientificName()).isNotEqualTo(englishProduct.scientificName());
        assertThat(arabicProduct.price()).isEqualByComparingTo(englishProduct.price());
        assertThat(arabicProduct.imageUrl()).isEqualTo(englishProduct.imageUrl());
        assertThat(arabicProduct.categoryId()).isEqualTo(englishProduct.categoryId());
        assertThat(arabicProduct.categoryName()).isNotEqualTo(englishProduct.categoryName());
        assertThat(arabicProduct.company()).isNotEqualTo(englishProduct.company());
        assertThat(arabicProduct.route()).isNotEqualTo(englishProduct.route());
        assertThat(productService.getProductById(arabicProduct.id(), "ar")).isEqualTo(arabicProduct);
        assertThat(productService.searchProducts(
                arabicProduct.name(),
                "ar",
                PageRequest.of(0, 20, Sort.by("name"))
        ).content()).contains(arabicProduct);
        assertThat(productService.getProductsByCategory(
                arabicProduct.categoryId(),
                "ar",
                PageRequest.of(0, 20, Sort.by("name"))
        ).content()).contains(arabicProduct);
        assertThat(productService.getAllProducts(
                "ar",
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "price"))
        ).content()).hasSize(20);
    }

    @Test
    @Transactional
    void supportsCategoryCrud() {
        PaginatedResponse<CategoryResponse> categories = categoryService.getAllCategories(
                PageRequest.of(0, 10, Sort.by("name"))
        );
        assertThat(categories.content()).hasSize(10);
        assertThat(categories.totalElements()).isEqualTo(226);

        CategoryResponse created = categoryService.createCategory(new CreateCategoryRequest("test category"));
        assertThat(created.name()).isEqualTo("TEST CATEGORY");
        assertThat(categoryService.getCategoryById(created.id())).isEqualTo(created);

        CategoryResponse updated = categoryService.updateCategory(
                created.id(),
                new UpdateCategoryRequest("updated category")
        );
        assertThat(updated.name()).isEqualTo("UPDATED CATEGORY");

        categoryService.deleteCategory(created.id());
        assertThat(categoryRepository.existsById(created.id())).isFalse();
    }

    @Test
    @Transactional
    void synchronizesTranslationsWhenProductsAlreadyExist() throws IOException {
        productDataInitializer.run(new DefaultApplicationArguments());

        assertThat(productRepository.count()).isEqualTo(798);
        assertThat(productTranslationRepository.findAllByLang("ar")).hasSize(798);
    }
}
