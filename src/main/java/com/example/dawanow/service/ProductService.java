package com.example.dawanow.service;

import com.example.dawanow.dtos.request.CreateProductRequest;
import com.example.dawanow.dtos.request.UpdateProductRequest;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.dtos.response.ProductResponse;
import com.example.dawanow.entity.Category;
import com.example.dawanow.entity.CategoryTranslation;
import com.example.dawanow.entity.Product;
import com.example.dawanow.entity.ProductTranslation;
import com.example.dawanow.exception.ResourceNotFoundException;
import com.example.dawanow.mapper.ProductMapper;
import com.example.dawanow.repo.CategoryRepository;
import com.example.dawanow.repo.CategoryTranslationRepository;
import com.example.dawanow.repo.ProductRepository;
import com.example.dawanow.repo.ProductTranslationRepository;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {

    private static final String ENGLISH = "en";
    private static final String ARABIC = "ar";
    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "id",
            "name",
            "productName",
            "strength",
            "packSize",
            "form",
            "scientificName",
            "scientificCategory",
            "price",
            "company",
            "route"
    );
    private static final Map<String, String> ARABIC_SORT_FIELDS = Map.ofEntries(
            Map.entry("id", "product.id"),
            Map.entry("name", "name"),
            Map.entry("productName", "productName"),
            Map.entry("strength", "strength"),
            Map.entry("packSize", "packSize"),
            Map.entry("form", "form"),
            Map.entry("scientificName", "scientificName"),
            Map.entry("scientificCategory", "scientificCategory"),
            Map.entry("price", "product.price"),
            Map.entry("company", "company"),
            Map.entry("route", "route")
    );

    private final ProductRepository productRepository;
    private final ProductTranslationRepository productTranslationRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryTranslationRepository categoryTranslationRepository;
    private final ProductMapper productMapper;

    @Transactional(readOnly = true)
    public PaginatedResponse<ProductResponse> getAllProducts(String lang, Pageable pageable) {
        return getAllProducts(lang, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<ProductResponse> getAllProducts(
            String lang,
            String company,
            Long categoryId,
            Pageable pageable
    ) {
        String language = normalizeLanguage(lang);
        Pageable validatedPageable = validateSort(pageable);
        String companyFilter = normalizeOptionalFilter(company);
        if (categoryId != null && !categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category not found");
        }

        if (ARABIC.equals(language)) {
            Page<ProductResponse> products = productTranslationRepository
                    .findAllFiltered(
                            ARABIC,
                            companyFilter,
                            categoryId,
                            toArabicPageable(validatedPageable)
                    )
                    .map(productMapper::toResponse);
            return PaginatedResponse.from(products);
        }

        return PaginatedResponse.from(
                productRepository.findAllFiltered(companyFilter, categoryId, validatedPageable)
                        .map(productMapper::toResponse)
        );
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<ProductResponse> searchProducts(String keyword, String lang, Pageable pageable) {
        String language = normalizeLanguage(lang);
        if (!StringUtils.hasText(keyword)) {
            return getAllProducts(language, pageable);
        }

        String searchTerm = keyword.trim();
        Pageable validatedPageable = validateSort(pageable);
        if (ARABIC.equals(language)) {
            return PaginatedResponse.from(
                    productTranslationRepository.search(
                            ARABIC,
                            searchTerm,
                            toArabicPageable(validatedPageable)
                    ).map(productMapper::toResponse)
            );
        }

        return PaginatedResponse.from(productRepository
                .search(searchTerm, validatedPageable)
                .map(productMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<ProductResponse> getProductsByCategory(
            Long categoryId,
            String lang,
            Pageable pageable
    ) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category not found");
        }

        String language = normalizeLanguage(lang);
        Pageable validatedPageable = validateSort(pageable);
        if (ARABIC.equals(language)) {
            return PaginatedResponse.from(
                    productTranslationRepository.findByLangAndProductCategoryId(
                            ARABIC,
                            categoryId,
                            toArabicPageable(validatedPageable)
                    ).map(productMapper::toResponse)
            );
        }

        return PaginatedResponse.from(
                productRepository.findByCategoryId(categoryId, validatedPageable).map(productMapper::toResponse)
        );
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id, String lang) {
        Product product = findProductById(id);
        if (ARABIC.equals(normalizeLanguage(lang))) {
            return productMapper.toResponse(findArabicTranslation(product.getId()));
        }
        return productMapper.toResponse(product);
    }

    public ProductResponse createProduct(CreateProductRequest request) {
        Category category = findCategoryById(request.categoryId());

        Product product = new Product();
        product.setProductName(requireText(request.productName(), "Product name"));
        product.setStrength(optionalText(request.strength()));
        product.setPackSize(optionalText(request.packSize()));
        product.setForm(requireText(request.form(), "Product form"));
        product.setName(buildDisplayName(
                product.getProductName(),
                product.getStrength(),
                product.getPackSize(),
                product.getForm()
        ));
        product.setScientificName(requireText(request.scientificName(), "Scientific name"));
        product.setScientificCategory(
                requireText(request.scientificCategory(), "Scientific category")
        );
        product.setPrice(requirePositivePrice(request.price()));
        product.setImageUrl(requireText(request.imageUrl(), "Image URL"));
        product.setCategory(category);
        product.setCompany(requireText(request.company(), "Product company"));
        product.setRoute(requireText(request.route(), "Product route"));
        product.setDescription(requireText(request.description(), "Product description"));

        Product savedProduct = productRepository.save(product);
        ProductTranslation translation = new ProductTranslation();
        translation.setProduct(savedProduct);
        translation.setLang(ARABIC);
        translation.setName(requireText(request.translatedName(), "Translated product name"));
        translation.setProductName(
                requireText(request.translatedProductName(), "Translated product base name")
        );
        translation.setStrength(optionalText(request.translatedStrength()));
        translation.setPackSize(optionalText(request.translatedPackSize()));
        translation.setForm(requireText(request.translatedForm(), "Translated product form"));
        translation.setScientificName(
                requireText(request.translatedScientificName(), "Translated scientific name")
        );
        translation.setScientificCategory(
                requireText(request.translatedScientificCategory(), "Translated scientific category")
        );
        translation.setConsumerCategory(
                requireText(request.translatedConsumerCategory(), "Translated consumer category")
        );
        translation.setCompany(requireText(request.translatedCompany(), "Translated company"));
        translation.setRoute(requireText(request.translatedRoute(), "Translated route"));
        translation.setDescription(
                requireText(request.translatedDescription(), "Translated description")
        );
        ensureCategoryTranslation(category, translation.getConsumerCategory());
        productTranslationRepository.save(translation);

        return productMapper.toResponse(savedProduct);
    }

    public ProductResponse updateProduct(Long id, UpdateProductRequest request) {
        Product product = findProductById(id);
        boolean displayNameChanged = false;
        boolean categoryChanged = false;

        if (request.productName() != null) {
            product.setProductName(requireText(request.productName(), "Product name"));
            displayNameChanged = true;
        }
        if (request.strength() != null) {
            product.setStrength(optionalText(request.strength()));
            displayNameChanged = true;
        }
        if (request.packSize() != null) {
            product.setPackSize(optionalText(request.packSize()));
            displayNameChanged = true;
        }
        if (request.form() != null) {
            product.setForm(requireText(request.form(), "Product form"));
            displayNameChanged = true;
        }
        if (displayNameChanged) {
            product.setName(buildDisplayName(
                    product.getProductName(),
                    product.getStrength(),
                    product.getPackSize(),
                    product.getForm()
            ));
        }
        if (request.scientificName() != null) {
            product.setScientificName(requireText(request.scientificName(), "Scientific name"));
        }
        if (request.scientificCategory() != null) {
            product.setScientificCategory(
                    requireText(request.scientificCategory(), "Scientific category")
            );
        }
        if (request.price() != null) {
            product.setPrice(requirePositivePrice(request.price()));
        }
        if (request.imageUrl() != null) {
            product.setImageUrl(requireText(request.imageUrl(), "Image URL"));
        }
        if (request.categoryId() != null) {
            product.setCategory(findCategoryById(request.categoryId()));
            categoryChanged = true;
        }
        if (request.company() != null) {
            product.setCompany(requireText(request.company(), "Product company"));
        }
        if (request.route() != null) {
            product.setRoute(requireText(request.route(), "Product route"));
        }
        if (request.description() != null) {
            product.setDescription(requireText(request.description(), "Product description"));
        }

        updateArabicTranslation(product, request, categoryChanged);
        return productMapper.toResponse(product);
    }

    public void deleteProduct(Long id) {
        Product product = findProductById(id);
        productRepository.delete(product);
    }

    private Product findProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private Category findCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    private ProductTranslation findArabicTranslation(Long productId) {
        return productTranslationRepository.findByProductIdAndLang(productId, ARABIC)
                .orElseThrow(() -> new ResourceNotFoundException("Arabic product translation not found"));
    }

    private void updateArabicTranslation(
            Product product,
            UpdateProductRequest request,
            boolean categoryChanged
    ) {
        if (request.translatedName() == null
                && request.translatedProductName() == null
                && request.translatedStrength() == null
                && request.translatedPackSize() == null
                && request.translatedForm() == null
                && request.translatedScientificName() == null
                && request.translatedScientificCategory() == null
                && request.translatedConsumerCategory() == null
                && request.translatedCompany() == null
                && request.translatedRoute() == null
                && request.translatedDescription() == null
                && !categoryChanged) {
            return;
        }

        ProductTranslation translation = findArabicTranslation(product.getId());
        if (request.translatedName() != null) {
            translation.setName(requireText(request.translatedName(), "Translated product name"));
        }
        if (request.translatedProductName() != null) {
            translation.setProductName(
                    requireText(request.translatedProductName(), "Translated product base name")
            );
        }
        if (request.translatedStrength() != null) {
            translation.setStrength(optionalText(request.translatedStrength()));
        }
        if (request.translatedPackSize() != null) {
            translation.setPackSize(optionalText(request.translatedPackSize()));
        }
        if (request.translatedForm() != null) {
            translation.setForm(requireText(request.translatedForm(), "Translated product form"));
        }
        if (request.translatedScientificName() != null) {
            translation.setScientificName(
                    requireText(request.translatedScientificName(), "Translated scientific name")
            );
        }
        if (request.translatedScientificCategory() != null) {
            translation.setScientificCategory(
                    requireText(request.translatedScientificCategory(), "Translated scientific category")
            );
        }
        if (request.translatedConsumerCategory() != null) {
            String translatedCategory = requireText(
                    request.translatedConsumerCategory(),
                    "Translated consumer category"
            );
            ensureCategoryTranslation(product.getCategory(), translatedCategory);
            translation.setConsumerCategory(translatedCategory);
        } else if (categoryChanged) {
            translation.setConsumerCategory(categoryTranslationRepository
                    .findByCategoryIdAndLang(product.getCategory().getId(), ARABIC)
                    .map(CategoryTranslation::getName)
                    .orElse(product.getCategory().getName()));
        }
        if (request.translatedCompany() != null) {
            translation.setCompany(requireText(request.translatedCompany(), "Translated company"));
        }
        if (request.translatedRoute() != null) {
            translation.setRoute(requireText(request.translatedRoute(), "Translated route"));
        }
        if (request.translatedDescription() != null) {
            translation.setDescription(
                    requireText(request.translatedDescription(), "Translated description")
            );
        }
    }

    private void ensureCategoryTranslation(Category category, String translatedName) {
        categoryTranslationRepository.findByCategoryIdAndLang(category.getId(), ARABIC)
                .ifPresentOrElse(existing -> {
                    if (!existing.getName().equals(translatedName)) {
                        throw new IllegalArgumentException(
                                "Translated consumer category does not match the category translation"
                        );
                    }
                }, () -> {
                    CategoryTranslation translation = new CategoryTranslation();
                    translation.setCategory(category);
                    translation.setLang(ARABIC);
                    translation.setName(translatedName);
                    categoryTranslationRepository.save(translation);
                });
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }

        return value.trim();
    }

    private String optionalText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String buildDisplayName(
            String productName,
            String strength,
            String packSize,
            String form
    ) {
        return java.util.stream.Stream.of(productName, strength, packSize, form)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private BigDecimal requirePositivePrice(BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("Product price must be positive");
        }
        return price;
    }

    private String normalizeOptionalFilter(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Pageable validateSort(Pageable pageable) {
        pageable.getSort().forEach(order -> {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new IllegalArgumentException("Invalid product sort field: " + order.getProperty());
            }
        });
        return pageable;
    }

    private Pageable toArabicPageable(Pageable pageable) {
        Sort localizedSort = Sort.by(pageable.getSort().stream()
                .map(order -> order.withProperty(ARABIC_SORT_FIELDS.get(order.getProperty())))
                .toList());

        if (pageable.isUnpaged()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), localizedSort);
    }

    private String normalizeLanguage(String lang) {
        String language = StringUtils.hasText(lang)
                ? lang.trim().toLowerCase(Locale.ROOT)
                : ENGLISH;
        if (!ENGLISH.equals(language) && !ARABIC.equals(language)) {
            throw new IllegalArgumentException("Unsupported language. Supported values are en and ar");
        }
        return language;
    }
}
