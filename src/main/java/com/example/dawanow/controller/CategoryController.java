package com.example.dawanow.controller;

import com.example.dawanow.dtos.request.CreateCategoryRequest;
import com.example.dawanow.dtos.request.UpdateCategoryRequest;
import com.example.dawanow.dtos.response.ApiResponse;
import com.example.dawanow.dtos.response.CategoryResponse;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Browse and manage product categories")
public class CategoryController {

    private static final String INVALID_SORT_EXAMPLE =
            "{\"success\":false,\"message\":\"Invalid category sort value\",\"data\":null}";
    private static final String CATEGORY_NOT_FOUND_EXAMPLE =
            "{\"success\":false,\"message\":\"Category not found\",\"data\":null}";
    private static final String INVALID_NAME_EXAMPLE =
            "{\"success\":false,\"message\":\"Category name already exists\",\"data\":null}";
    private static final String CATEGORY_IN_USE_EXAMPLE =
            "{\"success\":false,\"message\":\"Category cannot be deleted while it has products\",\"data\":null}";

    private final CategoryService categoryService;

    @GetMapping
    @Operation(
            summary = "Get all categories",
            description = "Public endpoint that returns categories in a paginated response. "
                    + "Set lang=ar to return Arabic category names; the default language is English. "
                    + "Use page and size for pagination and sort by id or name using asc or desc, "
                    + "for example sort=name,asc."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Categories fetched successfully with pagination metadata"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid pagination or sort value",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = INVALID_SORT_EXAMPLE)
                    )
            )
    })
    public ResponseEntity<ApiResponse<PaginatedResponse<CategoryResponse>>> getAllCategories(
            @Parameter(description = "Response language: en or ar", example = "en")
            @RequestParam(defaultValue = "en") String lang,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable
    ) {
        return ResponseEntity.ok(
                ApiResponse.success("Categories fetched", categoryService.getAllCategories(lang, pageable))
        );
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get category by ID",
            description = "Returns one category. Set lang=ar to return its Arabic name; the default language "
                    + "is English. This endpoint is public."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Category fetched successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Category not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = CATEGORY_NOT_FOUND_EXAMPLE)
                    )
            )
    })
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategoryById(
            @Parameter(description = "Category ID", example = "1", required = true)
            @PathVariable Long id,
            @Parameter(description = "Response language: en or ar", example = "en")
            @RequestParam(defaultValue = "en") String lang
    ) {
        return ResponseEntity.ok(
                ApiResponse.success("Category fetched", categoryService.getCategoryById(id, lang))
        );
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Create a category",
            description = "Admin only. Creates a uniquely named category. The name is trimmed and stored in uppercase.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Category created successfully and returned"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Name is invalid or already exists",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = INVALID_NAME_EXAMPLE)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Administrator role is required"
            )
    })
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Category name",
                    required = true
            )
            @Valid @RequestBody CreateCategoryRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Category created", categoryService.createCategory(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Update a category",
            description = "Admin only. Updates the category name. An omitted name keeps the current value; "
                    + "a supplied name is trimmed and stored in uppercase.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Category updated successfully and returned"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Name is invalid or already exists",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = INVALID_NAME_EXAMPLE)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Administrator role is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Category not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = CATEGORY_NOT_FOUND_EXAMPLE)
                    )
            )
    })
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @Parameter(description = "Category ID", example = "1", required = true)
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Category fields to update",
                    required = true
            )
            @Valid @RequestBody UpdateCategoryRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Category updated", categoryService.updateCategory(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Delete a category",
            description = "Admin only. Deletes a category only when no products currently reference it.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Category deleted successfully; response data is null"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Category still has products and cannot be deleted",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = CATEGORY_IN_USE_EXAMPLE)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Administrator role is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Category not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = CATEGORY_NOT_FOUND_EXAMPLE)
                    )
            )
    })
    public ResponseEntity<ApiResponse<Void>> deleteCategory(
            @Parameter(description = "Category ID", example = "1", required = true)
            @PathVariable Long id
    ) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.success("Category deleted"));
    }
}
