package com.example.dawanow.service;

import com.example.dawanow.dtos.request.CreateCategoryRequest;
import com.example.dawanow.dtos.request.UpdateCategoryRequest;
import com.example.dawanow.dtos.response.CategoryResponse;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.entity.Category;
import com.example.dawanow.entity.CategoryTranslation;
import com.example.dawanow.exception.ResourceNotFoundException;
import com.example.dawanow.mapper.CategoryMapper;
import com.example.dawanow.repo.CategoryRepository;
import com.example.dawanow.repo.CategoryTranslationRepository;
import com.example.dawanow.repo.ProductRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {

    private static final String ENGLISH = "en";
    private static final String ARABIC = "ar";
    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "name");

    private final CategoryRepository categoryRepository;
    private final CategoryTranslationRepository categoryTranslationRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;

    @Transactional(readOnly = true)
    public PaginatedResponse<CategoryResponse> getAllCategories(String lang, Pageable pageable) {
        String language = normalizeLanguage(lang);
        Page<Category> categories = categoryRepository.findAll(validateSort(pageable));
        if (ENGLISH.equals(language) || categories.isEmpty()) {
            return PaginatedResponse.from(categories.map(categoryMapper::toResponse));
        }

        List<Long> categoryIds = categories.stream().map(Category::getId).toList();
        Map<Long, CategoryTranslation> translations = categoryTranslationRepository
                .findAllByCategoryIdInAndLang(categoryIds, ARABIC)
                .stream()
                .collect(Collectors.toMap(
                        translation -> translation.getCategory().getId(),
                        Function.identity()
                ));

        return PaginatedResponse.from(categories.map(category -> {
            CategoryTranslation translation = translations.get(category.getId());
            return translation == null
                    ? categoryMapper.toResponse(category)
                    : categoryMapper.toResponse(translation);
        }));
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id, String lang) {
        Category category = findCategoryById(id);
        if (ARABIC.equals(normalizeLanguage(lang))) {
            return categoryTranslationRepository.findByCategoryIdAndLang(id, ARABIC)
                    .map(categoryMapper::toResponse)
                    .orElseGet(() -> categoryMapper.toResponse(category));
        }
        return categoryMapper.toResponse(category);
    }

    public CategoryResponse createCategory(CreateCategoryRequest request) {
        String name = normalizeName(request.name());
        ensureNameIsAvailable(name, null);

        Category category = new Category();
        category.setName(name);

        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    public CategoryResponse updateCategory(Long id, UpdateCategoryRequest request) {
        Category category = findCategoryById(id);

        if (request.name() != null) {
            String name = normalizeName(request.name());
            ensureNameIsAvailable(name, id);
            category.setName(name);
        }

        return categoryMapper.toResponse(category);
    }

    public void deleteCategory(Long id) {
        Category category = findCategoryById(id);
        if (productRepository.existsByCategoryId(id)) {
            throw new IllegalArgumentException("Category cannot be deleted while it has products");
        }

        categoryRepository.delete(category);
    }

    private Category findCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    private void ensureNameIsAvailable(String name, Long currentCategoryId) {
        categoryRepository.findByNameIgnoreCase(name).ifPresent(category -> {
            if (!category.getId().equals(currentCategoryId)) {
                throw new IllegalArgumentException("Category name already exists");
            }
        });
    }

    private String normalizeName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Category name cannot be blank");
        }

        return name.trim().toUpperCase(Locale.ROOT);
    }

    private Pageable validateSort(Pageable pageable) {
        pageable.getSort().forEach(order -> {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new IllegalArgumentException("Invalid category sort field: " + order.getProperty());
            }
        });
        return pageable;
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
