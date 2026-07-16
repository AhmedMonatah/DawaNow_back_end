package com.example.dawanow.service;

import com.example.dawanow.dtos.request.CreateProductRequest;
import com.example.dawanow.dtos.request.UpdateProductRequest;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.dtos.response.ProductResponse;
import com.example.dawanow.entity.Category;
import com.example.dawanow.entity.Product;
import com.example.dawanow.entity.ProductTranslation;
import com.example.dawanow.exception.ResourceNotFoundException;
import com.example.dawanow.mapper.ProductMapper;
import com.example.dawanow.repo.CategoryRepository;
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
            "scientificName",
            "price",
            "company",
            "route"
    );
    private static final Map<String, String> ARABIC_SORT_FIELDS = Map.of(
            "id", "product.id",
            "name", "name",
            "scientificName", "scientificName",
            "price", "product.price",
            "company", "company",
            "route", "route"
    );

    private final ProductRepository productRepository;
    private final ProductTranslationRepository productTranslationRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    @Transactional(readOnly = true)
    public PaginatedResponse<ProductResponse> getAllProducts(String lang, Pageable pageable) {
        String language = normalizeLanguage(lang);
        Pageable validatedPageable = validateSort(pageable);

        if (ARABIC.equals(language)) {
            Page<ProductResponse> products = productTranslationRepository
                    .findByLang(ARABIC, toArabicPageable(validatedPageable))
                    .map(productMapper::toResponse);
            return PaginatedResponse.from(products);
        }

        return PaginatedResponse.from(
                productRepository.findAll(validatedPageable).map(productMapper::toResponse)
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
                .findByNameContainingIgnoreCaseOrScientificNameContainingIgnoreCase(
                        searchTerm,
                        searchTerm,
                        validatedPageable
                )
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
        product.setName(requireText(request.name(), "Product name"));
        product.setScientificName(requireText(request.scientificName(), "Scientific name"));
        product.setPrice(requirePositivePrice(request.price()));
        product.setImageUrl(requireText(request.imageUrl(), "Image URL"));
        product.setCategory(category);
        product.setCompany(requireText(request.company(), "Product company"));
        product.setRoute(requireText(request.route(), "Product route"));

        Product savedProduct = productRepository.save(product);
        ProductTranslation translation = new ProductTranslation();
        translation.setProduct(savedProduct);
        translation.setLang(ARABIC);
        translation.setName(requireText(request.translatedName(), "Translated product name"));
        translation.setScientificName(
                requireText(request.translatedScientificName(), "Translated scientific name")
        );
        translation.setCategoryName(
                requireText(request.translatedCategoryName(), "Translated category name")
        );
        translation.setCompany(requireText(request.translatedCompany(), "Translated company"));
        translation.setRoute(requireText(request.translatedRoute(), "Translated route"));
        productTranslationRepository.save(translation);

        return productMapper.toResponse(savedProduct);
    }

    public ProductResponse updateProduct(Long id, UpdateProductRequest request) {
        Product product = findProductById(id);

        if (request.name() != null) {
            product.setName(requireText(request.name(), "Product name"));
        }
        if (request.scientificName() != null) {
            product.setScientificName(requireText(request.scientificName(), "Scientific name"));
        }
        if (request.price() != null) {
            product.setPrice(requirePositivePrice(request.price()));
        }
        if (request.imageUrl() != null) {
            product.setImageUrl(requireText(request.imageUrl(), "Image URL"));
        }
        if (request.categoryId() != null) {
            product.setCategory(findCategoryById(request.categoryId()));
        }
        if (request.company() != null) {
            product.setCompany(requireText(request.company(), "Product company"));
        }
        if (request.route() != null) {
            product.setRoute(requireText(request.route(), "Product route"));
        }

        updateArabicTranslation(product, request);
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

    private void updateArabicTranslation(Product product, UpdateProductRequest request) {
        if (request.translatedName() == null
                && request.translatedScientificName() == null
                && request.translatedCategoryName() == null
                && request.translatedCompany() == null
                && request.translatedRoute() == null) {
            return;
        }

        ProductTranslation translation = findArabicTranslation(product.getId());
        if (request.translatedName() != null) {
            translation.setName(requireText(request.translatedName(), "Translated product name"));
        }
        if (request.translatedScientificName() != null) {
            translation.setScientificName(
                    requireText(request.translatedScientificName(), "Translated scientific name")
            );
        }
        if (request.translatedCategoryName() != null) {
            translation.setCategoryName(
                    requireText(request.translatedCategoryName(), "Translated category name")
            );
        }
        if (request.translatedCompany() != null) {
            translation.setCompany(requireText(request.translatedCompany(), "Translated company"));
        }
        if (request.translatedRoute() != null) {
            translation.setRoute(requireText(request.translatedRoute(), "Translated route"));
        }
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }

        return value.trim();
    }

    private BigDecimal requirePositivePrice(BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("Product price must be positive");
        }
        return price;
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
