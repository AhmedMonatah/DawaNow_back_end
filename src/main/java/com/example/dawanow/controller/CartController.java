package com.example.dawanow.controller;

import com.example.dawanow.dtos.request.AddCartItemRequest;
import com.example.dawanow.dtos.response.ApiResponse;
import com.example.dawanow.dtos.response.CartResponse;
import com.example.dawanow.repo.CartRepository;
import com.example.dawanow.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "Manage the authenticated user's shopping cart")
public class CartController {

    private final CartRepository cartRepository;
    private final CartService cartService;

    @GetMapping
    @Operation(
            summary = "Get current cart",
            description = "Returns the authenticated user's cart including all items and the total price.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Cart fetched successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            )
    })
    public ResponseEntity<ApiResponse<CartResponse>> getCart() {
        CartResponse cartResponse = cartService.getCart();
        return ResponseEntity.ok(ApiResponse.success("Cart fetched", cartResponse));
    }

    @PostMapping("/items")
    @Operation(
            summary = "Add product to cart",
            description = "Adds a product to the authenticated user's cart. If the product already exists in the cart, its quantity is increased.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Product added successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid request"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Product not found"
            )
    })
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Product and quantity to add",
                    required = true
            )
            @Valid @RequestBody AddCartItemRequest request) {
        CartResponse cartResponse =  cartService.addItem(request);
        return ResponseEntity.ok(ApiResponse.success("Product added to cart", cartResponse));
    }

    @PatchMapping("/items/{cartItemId}")
    @Operation(
            summary = "Set product quantity",
            description = "Updates the absolute quantity of an existing cart item in the authenticated user's cart. If the quantity is set to 0 or less, the item is removed.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "CartItem quantity updated successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid quantity value"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "CartItem not found in cart"
            )
    })
    public ResponseEntity<ApiResponse<CartResponse>> setQuantity(
            @Parameter(
                    description = "CartItem ID",
                    example = "5",
                    required = true
            )
            @PathVariable Long cartItemId,
            @Parameter(
                    description = "The absolute target quantity to set",
                    example = "3",
                    required = true
            )
            @RequestBody    @Min(value = 1, message = "Quantity must be at least 1") long newQuantity) {

           CartResponse cartResponse = cartService.setQuantity(cartItemId, newQuantity);
           return ResponseEntity.ok(ApiResponse.success("CartItem quantity updated", cartResponse));
    }

    @DeleteMapping("/items/{cartItemId}")
    @Operation(
            summary = "Remove cart item from cart",
            description = "Completely removes a cart item from the authenticated user's cart.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Cart Item removed successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Cart Item not found in cart"
            )
    })
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            @Parameter(
                    description = "Cart Item ID",
                    example = "5",
                    required = true
            )
            @PathVariable Long cartItemId) {
       CartResponse cartResponse = cartService.removeItem(cartItemId);
       return ResponseEntity.ok(ApiResponse.success("Cart Item  removed from cart", cartResponse));
    }

    @DeleteMapping
    @Operation(
            summary = "Clear cart",
            description = "Removes all products from the authenticated user's cart.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Cart cleared successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            )
    })
    public ResponseEntity<ApiResponse<Void>> clearCart() {
        cartService.clearCart();
        return ResponseEntity.ok(ApiResponse.success("Cart cleared"));
    }

    @GetMapping("/count")
    @Operation(
            summary = "Get total items count",
            description = "Returns the cumulative quantity of all items inside the authenticated user's cart.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Item count fetched successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            )
    })
    public ResponseEntity<ApiResponse<Long>> getItemCount() {
        Long count = cartService.getItemCount();
        return ResponseEntity.ok(ApiResponse.success("Item count fetched", count));
    }
}