package com.example.dawanow.service;

import com.example.dawanow.dtos.request.CreateCategoryRequest;
import com.example.dawanow.dtos.request.UpdateCategoryRequest;
import com.example.dawanow.dtos.response.CategoryResponse;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.entity.Category;
import com.example.dawanow.exception.ResourceNotFoundException;
import com.example.dawanow.mapper.CategoryMapper;
import com.example.dawanow.repo.CategoryRepository;
import com.example.dawanow.repo.ProductRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;

    @Transactional(readOnly = true)
    public PaginatedResponse<CategoryResponse> getAllCategories(Pageable pageable) {
        return PaginatedResponse.from(categoryRepository.findAll(pageable).map(categoryMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {
        return categoryMapper.toResponse(findCategoryById(id));
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

}
