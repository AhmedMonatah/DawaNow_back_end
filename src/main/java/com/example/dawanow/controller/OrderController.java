package com.example.dawanow.controller;

import com.example.dawanow.dtos.request.CreateOrderRequest;
import com.example.dawanow.dtos.response.ApiResponse;
import com.example.dawanow.dtos.response.OrderResponse;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.service.OrderService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order management for customers, pharmacists, and administrators")
public class OrderController {

    private static final String INVALID_ORDER_EXAMPLE =
            "{\"success\":false,\"message\":\"Only accepted offers can be used to create an order\",\"data\":null}";
    private static final String OFFER_NOT_FOUND_EXAMPLE =
            "{\"success\":false,\"message\":\"Offer not found\",\"data\":null}";
    private static final String PHARMACY_NOT_FOUND_EXAMPLE =
            "{\"success\":false,\"message\":\"Pharmacy not found\",\"data\":null}";
    private static final String ORDER_NOT_FOUND_EXAMPLE =
            "{\"success\":false,\"message\":\"Order not found\",\"data\":null}";

    private final OrderService orderService;

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            summary = "Get current customer orders",
            description = "Customer only. Returns paginated list of orders placed by the currently authenticated customer.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Orders fetched successfully with pagination metadata"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Customer role is required"
            )
    })
    public ResponseEntity<ApiResponse<PaginatedResponse<OrderResponse>>> getCurrentCustomerOrders(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("Orders fetched", orderService.getCurrentCustomerOrders(pageable)));
    }

    @GetMapping("/pharmacy/{pharmacyId}")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    @Operation(
            summary = "Get pharmacy orders",
            description = "Returns paginated orders for a specific pharmacy. Access is restricted to the pharmacist "
                    + "registered as that pharmacy or a system user with the ADMIN role.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Pharmacy orders fetched successfully with pagination metadata"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "The current pharmacist is not at this pharmacy admin, or the user is not a system admin"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Pharmacy not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = PHARMACY_NOT_FOUND_EXAMPLE)
                    )
            )
    })
    public ResponseEntity<ApiResponse<PaginatedResponse<OrderResponse>>> getPharmacyOrders(
            @Parameter(description = "Pharmacy ID", example = "1", required = true)
            @PathVariable Long pharmacyId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("Pharmacy orders fetched", orderService.getPharmacyOrders(pharmacyId, pageable)));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get all orders",
            description = "Admin only. Returns paginated list of all orders in the system.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Orders fetched successfully with pagination metadata"
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
    public ResponseEntity<ApiResponse<PaginatedResponse<OrderResponse>>> getAllOrders(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("Orders fetched", orderService.getAllOrders(pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'PHARMACIST', 'ADMIN')")
    @Operation(
            summary = "Get order by ID",
            description = "Returns one order only when the current user is its customer owner, the admin pharmacist "
                    + "of the pharmacy that received it, or a system user with the ADMIN role.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Order fetched successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "The current user does not own or administer this order"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Order not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = ORDER_NOT_FOUND_EXAMPLE)
                    )
            )
    })
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(
            @Parameter(description = "Order ID", example = "1", required = true)
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success("Order fetched", orderService.getOrderById(id)));
    }
}
