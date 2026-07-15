package com.example.dawanow.service;

import com.example.dawanow.dtos.request.CreateProductRequest;
import com.example.dawanow.dtos.request.UpdateProductRequest;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.dtos.response.ProductResponse;
import com.example.dawanow.entity.Category;
import com.example.dawanow.entity.Product;
import com.example.dawanow.exception.ResourceNotFoundException;
import com.example.dawanow.mapper.ProductMapper;
import com.example.dawanow.repo.CategoryRepository;
import com.example.dawanow.repo.ProductRepository;
import java.math.BigDecimal;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {

    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "id",
            "name",
            "arabicName",
            "scientificName",
            "price",
            "company",
            "route"
    );

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    @Transactional(readOnly = true)
    public PaginatedResponse<ProductResponse> getAllProducts(Pageable pageable) {
        return PaginatedResponse.from(productRepository.findAll(validateSort(pageable)).map(productMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<ProductResponse> searchProducts(String keyword, Pageable pageable) {
        if (!StringUtils.hasText(keyword)) {
            return getAllProducts(pageable);
        }

        String searchTerm = keyword.trim();
        return PaginatedResponse.from(productRepository
                .findByNameContainingIgnoreCaseOrArabicNameContainingIgnoreCaseOrScientificNameContainingIgnoreCase(
                        searchTerm,
                        searchTerm,
                        searchTerm,
                        validateSort(pageable)
                )
                .map(productMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<ProductResponse> getProductsByCategory(Long categoryId, Pageable pageable) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category not found");
        }

        return PaginatedResponse.from(
                productRepository.findByCategoryId(categoryId, validateSort(pageable)).map(productMapper::toResponse)
        );
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        return productMapper.toResponse(findProductById(id));
    }

    public ProductResponse createProduct(CreateProductRequest request) {
        Category category = findCategoryById(request.categoryId());

        Product product = new Product();
        product.setName(requireText(request.name(), "Product name"));
        product.setArabicName(requireText(request.arabicName(), "Arabic name"));
        product.setScientificName(requireText(request.scientificName(), "Scientific name"));
        product.setPrice(requirePositivePrice(request.price()));
        product.setImageUrl(requireText(request.imageUrl(), "Image URL"));
        product.setCategory(category);
        product.setCompany(requireText(request.company(), "Product company"));
        product.setRoute(requireText(request.route(), "Product route"));

        return productMapper.toResponse(productRepository.save(product));
    }

    public ProductResponse updateProduct(Long id, UpdateProductRequest request) {
        Product product = findProductById(id);

        if (request.name() != null) {
            product.setName(requireText(request.name(), "Product name"));
        }
        if (request.arabicName() != null) {
            product.setArabicName(requireText(request.arabicName(), "Arabic name"));
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
}
