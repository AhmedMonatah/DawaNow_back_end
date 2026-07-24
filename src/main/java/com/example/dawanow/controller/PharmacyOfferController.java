package com.example.dawanow.controller;

import com.example.dawanow.dtos.request.CreateOfferRequest;
import com.example.dawanow.dtos.response.*;
import com.example.dawanow.service.PharmacyOfferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.file.AccessDeniedException;

@RestController
@RequestMapping("/api/v1/offers")
@RequiredArgsConstructor
@Tag(name = "Pharmacy Offers", description = "Pharmacies respond to medicine requests and manage offer status")
public class PharmacyOfferController {

    private final PharmacyOfferService pharmacyOfferService;

    @GetMapping("/pharmacy/{pharmacyId}")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    @Operation(
            summary = "Get pharmacy offers",
            description = "Pharmacist or admin. Returns paginated list of offers made by a specific pharmacy.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Offers fetched successfully with pagination metadata"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Pharmacist or administrator role is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Pharmacy not found"
            )
    })
    public ResponseEntity<ApiResponse<PaginatedResponse<PharmacyOfferResponse>>> getPharmacyOffers(
            @Parameter(description = "Pharmacy ID", example = "1", required = true)
            @PathVariable Long pharmacyId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("Offers fetched", pharmacyOfferService.getOffersByPharmacy(pharmacyId, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'PHARMACIST', 'ADMIN')")
    @Operation(
            summary = "Get offer by ID",
            description = "Returns a single offer by its ID. Available to customers (for their requests), pharmacists, and admins.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Offer fetched successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Customer, pharmacist, or administrator role is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Offer not found"
            )
    })
    public ResponseEntity<ApiResponse<PharmacyOfferResponse>> getOfferById(
            @Parameter(description = "Offer ID", example = "1", required = true)
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success("Offer fetched", pharmacyOfferService.getOfferById(id)));
    }

    @PostMapping("/requests/{requestId}")
    @PreAuthorize("hasRole('PHARMACIST')")
    @Operation(
            summary = "Create a pharmacy offer",
            description = "Allows an authenticated pharmacist to submit an offer for a specific assigned medicine request.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    useReturnTypeSchema = true,
                    description = "Pharmacy offer created successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad request (e.g., duplicate offer or invalid items)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied / Request not assigned to your pharmacy"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Medicine request or product not found"
            )
    })
    public ResponseEntity<ApiResponse<PharmacyOfferResponse>> createPharmacyOffer(
            @PathVariable Long requestId,
            @Valid @RequestBody CreateOfferRequest request
    ) throws AccessDeniedException, BadRequestException {

        PharmacyOfferResponse offerResponse = pharmacyOfferService.createOffer(requestId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Pharmacy offer created successfully", offerResponse));
    }



    @GetMapping("requests/{requestId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            summary = "Get offers for a medicine request",
            description = "Customer only. Returns a paginated list of offers submitted by pharmacies for a specific medicine request.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Pharmacy offers fetched successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Customer role is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Medicine request not found"
            )
    })
    public ResponseEntity<ApiResponse<PaginatedResponse<PharmacyOfferResponse>>> getRequestOffers(
            @PathVariable Long requestId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        PaginatedResponse<PharmacyOfferResponse> paginatedResponse = pharmacyOfferService.getRequestOffers(requestId, pageable);

        return ResponseEntity.ok(ApiResponse.success(
                "Medicine request offers fetched successfully",
                paginatedResponse
        ));
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            summary = "Accept an offer",
            description = "Customer only. Accepts a pending pharmacy offer and creates a corresponding order.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Offer accepted and order created successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Offer is no longer pending or request is already fulfilled"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Customer role is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Offer not found"
            )
    })
    public ResponseEntity<ApiResponse<OrderResponse>> acceptOffer(
            @Parameter(description = "ID of the offer to accept", example = "1", required = true)
            @PathVariable Long id
    ) throws AccessDeniedException, BadRequestException {
        //pharmacyOfferService.acceptOffer(id);
        return ResponseEntity.ok(ApiResponse.success("Offer accepted and Order Created", null));
    }


    @PostMapping("/{medicineRequestId}/reject-all")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            summary = "Reject all offers for a request",
            description = "Customer only. Rejects all pending offers associated with a specific medicine request.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "All offers rejected successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "No offers found or offers are no longer pending"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Customer role is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Medicine request not found"
            )
    })
    public ResponseEntity<ApiResponse<Void>> rejectAllOffers(
            @Parameter(description = "Medicine Request ID", example = "1", required = true)
            @PathVariable Long medicineRequestId
    ) throws BadRequestException {
        //pharmacyOfferService.rejectAllOffers(medicineRequestId);
        return ResponseEntity.ok(ApiResponse.success("All offers rejected successfully"));
    }
}
