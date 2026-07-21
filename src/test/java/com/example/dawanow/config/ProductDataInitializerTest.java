package com.example.dawanow.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.dawanow.dtos.request.CreateCategoryRequest;
import com.example.dawanow.dtos.request.UpdateCategoryRequest;
import com.example.dawanow.dtos.response.CategoryResponse;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.dtos.response.ProductResponse;
import com.example.dawanow.entity.Category;
import com.example.dawanow.entity.CategoryTranslation;
import com.example.dawanow.entity.Product;
import com.example.dawanow.entity.ProductTranslation;
import com.example.dawanow.repo.CategoryRepository;
import com.example.dawanow.repo.CategoryTranslationRepository;
import com.example.dawanow.repo.ProductRepository;
import com.example.dawanow.repo.ProductTranslationRepository;
import com.example.dawanow.service.CategoryService;
import com.example.dawanow.service.ProductService;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
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
    private CategoryTranslationRepository categoryTranslationRepository;

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
        List<CategoryTranslation> categoryTranslations = categoryTranslationRepository.findAllByLang("ar");

        assertThat(products).hasSize(798);
        assertThat(categories).hasSize(24);
        assertThat(translations).hasSize(798);
        assertThat(categoryTranslations).hasSize(24);
        assertThat(translations)
                .extracting(ProductTranslation::getName)
                .noneMatch(name -> name.contains(BROWSER_TABS_TRANSLATION))
                .noneMatch(name -> name.contains(UNPAID_BILL_TRANSLATION));
        assertThat(categories)
                .extracting(Category::getName)
                .doesNotHaveDuplicates()
                .allMatch(name -> !name.isBlank() && name.equals(name.toUpperCase()));
        assertThat(products)
                .extracting(Product::getName, Product::getPrice)
                .doesNotHaveDuplicates();
        assertThat(products.stream()
                .map(product -> product.getCategory().getId())
                .collect(Collectors.toSet()))
                .hasSize(categories.size());
        assertThat(products).allSatisfy(product -> {
            assertThat(product.getName()).isNotBlank();
            assertThat(product.getProductName()).isNotBlank();
            assertThat(product.getForm()).isNotBlank();
            assertThat(product.getScientificName()).isNotBlank();
            assertThat(product.getScientificCategory()).isNotBlank();
            assertThat(product.getPrice()).isPositive();
            assertThat(product.getImageUrl()).startsWith("https://");
            assertThat(product.getCategory()).isNotNull();
            assertThat(product.getCategory().getId()).isNotNull();
            assertThat(product.getCompany()).isNotBlank();
            assertThat(product.getRoute()).isIn(VALID_ROUTES);
            assertThat(product.getDescription()).isNotBlank();
        });
        assertThat(translations).allSatisfy(translation -> {
            assertThat(translation.getProduct()).isNotNull();
            assertThat(translation.getLang()).isEqualTo("ar");
            assertThat(translation.getName()).containsPattern(ARABIC_TEXT);
            assertThat(translation.getProductName()).containsPattern(ARABIC_TEXT);
            assertThat(translation.getForm()).containsPattern(ARABIC_TEXT);
            assertThat(translation.getScientificName()).containsPattern(ARABIC_TEXT);
            assertThat(translation.getScientificCategory()).containsPattern(ARABIC_TEXT);
            assertThat(translation.getConsumerCategory()).containsPattern(ARABIC_TEXT);
            assertThat(translation.getCompany()).containsPattern(ARABIC_TEXT);
            assertThat(translation.getRoute()).containsPattern(ARABIC_TEXT);
            assertThat(translation.getDescription()).containsPattern(ARABIC_TEXT);
        });
        assertThat(categoryTranslations).allSatisfy(translation -> {
            assertThat(translation.getCategory()).isNotNull();
            assertThat(translation.getLang()).isEqualTo("ar");
            assertThat(translation.getName()).containsPattern(ARABIC_TEXT);
        });

        PaginatedResponse<ProductResponse> arabicProducts = productService.getAllProducts(
                "ar",
                PageRequest.of(0, 20, Sort.by("name"))
        );
        assertThat(arabicProducts.totalElements()).isEqualTo(798);
        assertThat(arabicProducts.content()).allSatisfy(product -> {
            assertThat(product.name()).containsPattern(ARABIC_TEXT);
            assertThat(product.scientificName()).containsPattern(ARABIC_TEXT);
            assertThat(product.scientificCategory()).containsPattern(ARABIC_TEXT);
            assertThat(product.consumerCategory()).containsPattern(ARABIC_TEXT);
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
        assertThat(arabicProduct.consumerCategory()).isNotEqualTo(englishProduct.consumerCategory());
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

        PaginatedResponse<ProductResponse> englishFilteredProducts = productService.getAllProducts(
                "en",
                englishProduct.company().toLowerCase(Locale.ROOT),
                englishProduct.categoryId(),
                PageRequest.of(0, 20, Sort.by("name"))
        );
        assertThat(englishFilteredProducts.content()).isNotEmpty().allSatisfy(product -> {
            assertThat(product.company()).isEqualToIgnoringCase(englishProduct.company());
            assertThat(product.categoryId()).isEqualTo(englishProduct.categoryId());
        });
        assertThat(productService.getAllProducts(
                "en",
                englishProduct.company(),
                null,
                PageRequest.of(0, 20, Sort.by("name"))
        ).content()).isNotEmpty().allSatisfy(product ->
                assertThat(product.company()).isEqualToIgnoringCase(englishProduct.company())
        );
        assertThat(productService.getAllProducts(
                "en",
                null,
                englishProduct.categoryId(),
                PageRequest.of(0, 20, Sort.by("name"))
        ).content()).isNotEmpty().allSatisfy(product ->
                assertThat(product.categoryId()).isEqualTo(englishProduct.categoryId())
        );

        PaginatedResponse<ProductResponse> arabicFilteredProducts = productService.getAllProducts(
                "ar",
                arabicProduct.company(),
                arabicProduct.categoryId(),
                PageRequest.of(0, 20, Sort.by("name"))
        );
        assertThat(arabicFilteredProducts.content()).isNotEmpty().allSatisfy(product -> {
            assertThat(product.company()).isEqualTo(arabicProduct.company());
            assertThat(product.categoryId()).isEqualTo(arabicProduct.categoryId());
        });
    }

    @Test
    @Transactional
    void supportsCategoryCrud() {
        PaginatedResponse<CategoryResponse> categories = categoryService.getAllCategories(
                "en",
                PageRequest.of(0, 10, Sort.by("name"))
        );
        assertThat(categories.content()).hasSize(10);
        assertThat(categories.totalElements()).isEqualTo(24);

        CategoryResponse created = categoryService.createCategory(new CreateCategoryRequest("test category"));
        assertThat(created.name()).isEqualTo("TEST CATEGORY");
        assertThat(categoryService.getCategoryById(created.id(), "en")).isEqualTo(created);
        assertThat(categoryService.getCategoryById(created.id(), "ar")).isEqualTo(created);

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
    void returnsLocalizedCategoriesWithStableIds() {
        PaginatedResponse<CategoryResponse> arabicCategories = categoryService.getAllCategories(
                "ar",
                PageRequest.of(0, 20, Sort.by("name"))
        );

        assertThat(arabicCategories.totalElements()).isEqualTo(24);
        assertThat(arabicCategories.content()).hasSize(20).allSatisfy(category ->
                assertThat(category.name()).containsPattern(ARABIC_TEXT)
        );

        CategoryResponse arabicCategory = arabicCategories.content().getFirst();
        CategoryResponse englishCategory = categoryService.getCategoryById(arabicCategory.id(), "en");
        assertThat(categoryService.getCategoryById(arabicCategory.id(), "ar")).isEqualTo(arabicCategory);
        assertThat(arabicCategory.id()).isEqualTo(englishCategory.id());
        assertThat(arabicCategory.name()).isNotEqualTo(englishCategory.name());

        assertThatThrownBy(() -> categoryService.getAllCategories(
                "fr",
                PageRequest.of(0, 20, Sort.by("name"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported language. Supported values are en and ar");
    }

    @Test
    @Transactional
    void synchronizesTranslationsWhenProductsAlreadyExist() throws IOException {
        productDataInitializer.run(new DefaultApplicationArguments());

        assertThat(productRepository.count()).isEqualTo(798);
        assertThat(productTranslationRepository.findAllByLang("ar")).hasSize(798);
        assertThat(categoryTranslationRepository.findAllByLang("ar")).hasSize(24);
    }
}
