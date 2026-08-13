package com.example.dawanow.controller;


import com.example.dawanow.dtos.response.ApiResponse;
import com.example.dawanow.dtos.response.MasterOrderResponse;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.service.MasterOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/masterorders")
@RequiredArgsConstructor
@Tag(name = "Master Orders", description = "Master Order management for customers")
public class MasterOrderController {


    private static final String INVALID_ORDER_EXAMPLE =
            "{\"success\":false,\"message\":\"Only accepted offers can be used to create an order\",\"data\":null}";
    private static final String OFFER_NOT_FOUND_EXAMPLE =
            "{\"success\":false,\"message\":\"Offer not found\",\"data\":null}";
    private static final String PHARMACY_NOT_FOUND_EXAMPLE =
            "{\"success\":false,\"message\":\"Pharmacy not found\",\"data\":null}";
    private static final String ORDER_NOT_FOUND_EXAMPLE =
            "{\"success\":false,\"message\":\"Order not found\",\"data\":null}";

    private final MasterOrderService orderService;

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    @Operation(
            summary = "Get Master order by ID",
            description = "Returns Master order only when the current user is its customer owner, the admin pharmacist "
                    + "of the pharmacy that received it, or a system user with the ADMIN role.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Master Order fetched successfully"
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
                    description = "Master Order not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = ORDER_NOT_FOUND_EXAMPLE)
                    )
            )
    })
    public ResponseEntity<ApiResponse<MasterOrderResponse>> getMasterOrderById(
            @Parameter(description = "Master Order ID", example = "1", required = true)
            @PathVariable Long id,
            @Parameter(description = "Response language: en or ar", example = "en")
            @RequestParam(defaultValue = "en") String lang
    ) {
        return ResponseEntity.ok(ApiResponse.success("Master Order fetched", orderService.getMasterOrderById(id, lang)));
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            summary = "Get current customer Master orders",
            description = "Customer only. Returns paginated list of master orders placed by the currently authenticated customer.",
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
    public ResponseEntity<ApiResponse<PaginatedResponse<MasterOrderResponse>>> getCurrentCustomerOrders(
            @Parameter(description = "Response language: en or ar", example = "en")
            @RequestParam(defaultValue = "en") String lang,
            @Parameter(description = "Filter by master order status", example = "PREPARING")
            @RequestParam(required = false) com.example.dawanow.entity.OrderStatus status,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("Master Orders fetched", orderService.getCurrentCustomerMasterOrders(lang, status, pageable)));
    }



}
