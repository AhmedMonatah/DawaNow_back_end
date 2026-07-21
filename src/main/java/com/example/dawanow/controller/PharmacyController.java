package com.example.dawanow.controller;

import com.example.dawanow.dtos.request.CreatePharmacyRequest;
import com.example.dawanow.dtos.request.UpdatePharmacyRequest;
import com.example.dawanow.dtos.response.*;
import com.example.dawanow.entity.Pharmacist;
import com.example.dawanow.entity.User;
import com.example.dawanow.service.MedicineRequestService;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/pharmacies")
@RequiredArgsConstructor
@Tag(name = "Pharmacies", description = "Browse and manage pharmacy information")
public class PharmacyController {

    private final PharmacyService pharmacyService;
    private final MedicineRequestService medicineRequestService;

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


    @GetMapping("/requests")
    @PreAuthorize("hasRole('PHARMACIST')")
    @Operation(
            summary = "Get current pharmacy requests",
            description = "Returns a paginated list of medicine requests assigned to the logged-in pharmacist's pharmacy.",
            security = @SecurityRequirement(name = "basicAuth"))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Pharmacy requests fetched successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Pharmacist role is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Pharmacist not assigned to any pharmacy"
            )
    })
    public ResponseEntity<ApiResponse<PaginatedResponse<MedicineRequestResponse>>> getCurrentPharmacyRequests(
            @ParameterObject Pageable pageable) {

        PaginatedResponse<MedicineRequestResponse> requests = medicineRequestService.getCurrentPharmacyRequests(pageable);

        return ResponseEntity.ok(ApiResponse.success("Pharmacy requests fetched successfully", requests));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('PHARMACIST')")
    @Operation(
            summary = "Get my pharmacy",
            description = "Returns the pharmacy the current pharmacist is assigned to, along with whether they are the admin.",
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
                    description = "Pharmacist role is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Pharmacist not assigned to any pharmacy"
            )
    })
    public ResponseEntity<ApiResponse<PharmacyMineResponse>> getMyPharmacy() {
        return ResponseEntity.ok(ApiResponse.success("Pharmacy fetched", pharmacyService.getMyPharmacy()));
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
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "413",
                    description = "Uploaded license file is too large"
            )
    })
    public ResponseEntity<ApiResponse<PharmacyResponse>> createPharmacy(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Pharmacy details",
                    required = true
            )
            @Valid @RequestPart("pharmacyRequest") CreatePharmacyRequest pharmacyRequest,
            @RequestPart("license") MultipartFile license
    ) {
        return ResponseEntity.ok(ApiResponse.success("Pharmacy created", pharmacyService.createPharmacy(pharmacyRequest, license)));
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

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    @Operation(
            summary = "Delete a pharmacy",
            description = "Platform admin or pharmacy admin only. Permanently deletes the pharmacy and unassigns all pharmacists.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Pharmacy deleted successfully; response data is null"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Only the platform admin or pharmacy admin can delete the pharmacy"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Pharmacy not found"
            )
    })
    public ResponseEntity<ApiResponse<Void>> deletePharmacy(
            @Parameter(description = "Pharmacy ID", example = "1", required = true)
            @PathVariable Long id
    ) {
        pharmacyService.deletePharmacy(id);
        return ResponseEntity.ok(ApiResponse.success("Pharmacy deleted"));
    }
}
