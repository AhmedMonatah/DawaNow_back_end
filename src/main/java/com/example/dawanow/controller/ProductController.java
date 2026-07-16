package com.example.dawanow.controller;

import com.example.dawanow.dtos.request.CreateProductRequest;
import com.example.dawanow.dtos.request.UpdateProductRequest;
import com.example.dawanow.dtos.response.ApiResponse;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.dtos.response.ProductResponse;
import com.example.dawanow.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Browse the medicine catalog and manage products")
@SecurityScheme(
        name = "basicAuth",
        description = "HTTP Basic authentication for protected endpoints",
        type = SecuritySchemeType.HTTP,
        scheme = "basic"
)
public class ProductController {

    private static final String INVALID_SORT_EXAMPLE =
            "{\"success\":false,\"message\":\"Invalid product sort field: unsupportedField\",\"data\":null}";
    private static final String PRODUCT_NOT_FOUND_EXAMPLE =
            "{\"success\":false,\"message\":\"Product not found\",\"data\":null}";
    private static final String CATEGORY_NOT_FOUND_EXAMPLE =
            "{\"success\":false,\"message\":\"Category not found\",\"data\":null}";
    private static final String VALIDATION_ERROR_EXAMPLE =
            "{\"success\":false,\"message\":\"price must be greater than 0\",\"data\":null}";

    private final ProductService productService;

    @GetMapping
    @Operation(
            summary = "Get all products",
            description = "Public endpoint that returns products in a paginated response. "
                    + "Use page and size for pagination. Sort supports id, name, arabicName, scientificName, "
                    + "price, company, and route with asc or desc, for example sort=price,desc. "
                    + "Repeat sort to order by multiple fields."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Products fetched successfully with pagination metadata"
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
    public ResponseEntity<ApiResponse<PaginatedResponse<ProductResponse>>> getAllProducts(
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("Products fetched", productService.getAllProducts(pageable)));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get product by ID",
            description = "Public endpoint that returns one product with its medicine details and category."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Product fetched successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Product not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = PRODUCT_NOT_FOUND_EXAMPLE)
                    )
            )
    })
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(
            @Parameter(description = "Product ID", example = "1", required = true)
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success("Product fetched", productService.getProductById(id)));
    }

    @GetMapping("/search")
    @Operation(
            summary = "Search products",
            description = "Public endpoint that searches product name, Arabic name, and scientific name. "
                    + "A missing or blank keyword returns all products. Results are paginated and support the same "
                    + "sort fields as the get-all endpoint."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Matching products fetched successfully with pagination metadata"
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
    public ResponseEntity<ApiResponse<PaginatedResponse<ProductResponse>>> searchProducts(
            @Parameter(
                    description = "Text matched against product, Arabic, and scientific names",
                    example = "Panadol"
            )
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("Products fetched", productService.searchProducts(keyword, pageable)));
    }

    @GetMapping("/category/{categoryId}")
    @Operation(
            summary = "Get products by category",
            description = "Public endpoint that returns the products belonging to a category. "
                    + "Results are paginated and support the same sort fields as the get-all endpoint."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Category products fetched successfully with pagination metadata"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid pagination or sort value",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = INVALID_SORT_EXAMPLE)
                    )
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
    public ResponseEntity<ApiResponse<PaginatedResponse<ProductResponse>>> getProductsByCategory(
            @Parameter(description = "Category ID", example = "1", required = true)
            @PathVariable Long categoryId,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("Products fetched", productService.getProductsByCategory(categoryId, pageable)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Create a product",
            description = "Admin only. Creates a medicine product and links it to an existing category. "
                    + "The route must be one of EAR, EFF, EYE, INJECTION, MOUTH, ORAL.LIQUID, ORAL.SOLID, "
                    + "RECTAL, SPRAY, or TOPICAL.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Product created successfully and returned"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = VALIDATION_ERROR_EXAMPLE)
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
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Complete product information",
                    required = true
            )
            @Valid @RequestBody CreateProductRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Product created", productService.createProduct(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Update a product",
            description = "Admin only. Partially updates a product; omitted fields keep their current values. "
                    + "When categoryId is supplied, it must identify an existing category.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Product updated successfully and returned"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = VALIDATION_ERROR_EXAMPLE)
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
                    description = "Product or supplied category not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = PRODUCT_NOT_FOUND_EXAMPLE)
                    )
            )
    })
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @Parameter(description = "Product ID", example = "1", required = true)
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Product fields to update",
                    required = true
            )
            @Valid @RequestBody UpdateProductRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Product updated", productService.updateProduct(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Delete a product",
            description = "Admin only. Permanently deletes an existing product.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Product deleted successfully; response data is null"
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
                    description = "Product not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = PRODUCT_NOT_FOUND_EXAMPLE)
                    )
            )
    })
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @Parameter(description = "Product ID", example = "1", required = true)
            @PathVariable Long id
    ) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Product deleted"));
    }
}
