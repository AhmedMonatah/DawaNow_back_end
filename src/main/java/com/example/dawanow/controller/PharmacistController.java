package com.example.dawanow.controller;

import com.example.dawanow.dtos.request.UpdatePharmacistProfileRequest;
import com.example.dawanow.dtos.response.ApiResponse;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.dtos.response.PharmacistResponse;
import com.example.dawanow.service.PharmacistService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pharmacists")
@RequiredArgsConstructor
@Tag(name = "Pharmacists", description = "Pharmacist profile and pharmacy membership management")
public class PharmacistController {

    private final PharmacistService pharmacistService;

    @GetMapping("/me")
    @PreAuthorize("hasRole('PHARMACIST')")
    @Operation(
            summary = "Get current pharmacist",
            description = "Returns the profile of the currently authenticated pharmacist, including pharmacy assignment.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Pharmacist profile returned"
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
    public ResponseEntity<ApiResponse<PharmacistResponse>> getCurrentPharmacist() {
        return ResponseEntity.ok(ApiResponse.success("Current pharmacist fetched", pharmacistService.getCurrentPharmacist()));
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('PHARMACIST')")
    @Operation(
            summary = "Update current pharmacist profile",
            description = "Partially updates the profile of the currently authenticated pharmacist; omitted fields keep their current values.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Pharmacist updated successfully and returned"
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
    public ResponseEntity<ApiResponse<PharmacistResponse>> updateCurrentPharmacist(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Pharmacist fields to update",
                    required = true
            )
            @Valid @RequestBody UpdatePharmacistProfileRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Pharmacist updated", pharmacistService.updateCurrentPharmacist(request)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get all pharmacists",
            description = "Admin only. Returns paginated list of all pharmacist accounts.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Pharmacists fetched successfully with pagination metadata"
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
    public ResponseEntity<ApiResponse<PaginatedResponse<PharmacistResponse>>> getAllPharmacists(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("Pharmacists fetched", pharmacistService.getAllPharmacists(pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get pharmacist by ID",
            description = "Admin only. Returns a single pharmacist by their ID.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Pharmacist fetched successfully"
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
                    description = "Pharmacist not found"
            )
    })
    public ResponseEntity<ApiResponse<PharmacistResponse>> getPharmacistById(
            @Parameter(description = "Pharmacist ID", example = "1", required = true)
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success("Pharmacist fetched", pharmacistService.getPharmacistById(id)));
    }

    @DeleteMapping("/me/pharmacy/{pharmacyId}")
    @PreAuthorize("hasRole('PHARMACIST')")
    @Operation(
            summary = "Leave a pharmacy",
            description = "Pharmacist removes themself from a pharmacy. The pharmacy admin cannot leave without transferring ownership.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Pharmacist left the pharmacy; response data is null"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Not assigned to this pharmacy or admin must transfer ownership first"
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
                    description = "Pharmacy not found"
            )
    })
    public ResponseEntity<ApiResponse<Void>> leavePharmacy(
            @Parameter(description = "Pharmacy ID to leave", example = "1", required = true)
            @PathVariable Long pharmacyId
    ) {
        pharmacistService.removeCurrentPharmacistFromPharmacy(pharmacyId);
        return ResponseEntity.ok(ApiResponse.success("You left the pharmacy"));
    }

    @DeleteMapping("/{id}/pharmacy/{pharmacyId}")
    @PreAuthorize("hasRole('PHARMACIST')")
    @Operation(
            summary = "Remove a pharmacist from a pharmacy",
            description = "Pharmacy admin removes another pharmacist from the pharmacy. The admin cannot be removed.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Pharmacist removed from pharmacy; response data is null"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Pharmacist not assigned to this pharmacy or cannot remove the admin"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Only the pharmacy admin can remove members"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Pharmacy or pharmacist not found"
            )
    })
    public ResponseEntity<ApiResponse<Void>> removePharmacistFromPharmacy(
            @Parameter(description = "Pharmacist ID to remove", example = "2", required = true)
            @PathVariable Long id,
            @Parameter(description = "Pharmacy ID", example = "1", required = true)
            @PathVariable Long pharmacyId
    ) {
        pharmacistService.removePharmacistFromPharmacy(pharmacyId, id);
        return ResponseEntity.ok(ApiResponse.success("Pharmacist removed from pharmacy"));
    }
}
