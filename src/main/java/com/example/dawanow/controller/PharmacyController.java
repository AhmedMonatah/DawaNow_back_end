package com.example.dawanow.controller;

import com.example.dawanow.dtos.request.CreatePharmacyRequest;
import com.example.dawanow.dtos.request.UpdatePharmacyRequest;
import com.example.dawanow.dtos.response.ApiResponse;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.dtos.response.PharmacyResponse;
import com.example.dawanow.service.PharmacyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pharmacies")
@RequiredArgsConstructor
@Tag(name = "Pharmacies", description = "Browse and manage pharmacy information")
public class PharmacyController {

    private final PharmacyService pharmacyService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'PHARMACIST', 'ADMIN')")
    @Operation(
            summary = "Get all pharmacies",
            description = "Returns paginated list of pharmacies. Available to all authenticated users.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Pharmacies fetched successfully with pagination metadata"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Customer, pharmacist, or administrator role is required"
            )
    })
    public ResponseEntity<ApiResponse<PaginatedResponse<PharmacyResponse>>> getAllPharmacies(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("Pharmacies fetched", pharmacyService.getAllPharmacies(pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'PHARMACIST', 'ADMIN')")
    @Operation(
            summary = "Get pharmacy by ID",
            description = "Returns a single pharmacy by its ID. Available to all authenticated users.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Pharmacy fetched successfully"
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
                    description = "Pharmacy not found"
            )
    })
    public ResponseEntity<ApiResponse<PharmacyResponse>> getPharmacyById(
            @Parameter(description = "Pharmacy ID", example = "1", required = true)
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success("Pharmacy fetched", pharmacyService.getPharmacyById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('PHARMACIST')")
    @Operation(
            summary = "Create a pharmacy",
            description = "Pharmacist only. Creates a new pharmacy and sets the creator as its admin. "
                    + "The pharmacist is automatically assigned to the new pharmacy.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Pharmacy created successfully and returned"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Pharmacist is already assigned to a pharmacy or request validation failed"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Pharmacist role is required"
            )
    })
    public ResponseEntity<ApiResponse<PharmacyResponse>> createPharmacy(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Pharmacy details",
                    required = true
            )
            @Valid @RequestBody CreatePharmacyRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Pharmacy created", pharmacyService.createPharmacy(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PHARMACIST')")
    @Operation(
            summary = "Update a pharmacy",
            description = "Pharmacy admin only. Partially updates the pharmacy information; omitted fields keep their current values.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Pharmacy updated successfully and returned"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Only the pharmacy admin can update the pharmacy"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Pharmacy not found"
            )
    })
    public ResponseEntity<ApiResponse<PharmacyResponse>> updatePharmacy(
            @Parameter(description = "Pharmacy ID", example = "1", required = true)
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Pharmacy fields to update",
                    required = true
            )
            @Valid @RequestBody UpdatePharmacyRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Pharmacy updated", pharmacyService.updatePharmacy(id, request)));
    }
}
