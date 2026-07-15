package com.example.dawanow.controller;

import com.example.dawanow.dtos.response.ApiResponse;
import com.example.dawanow.dtos.response.PharmacyCreationRequestResponse;
import com.example.dawanow.service.PharmacyCreationRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Pharmacy Creation Requests", description = "Pharmacists submit pharmacy creation requests with license documents for admin approval")
public class PharmacyCreationRequestController {

    private final PharmacyCreationRequestService pharmacyCreationRequestService;

    @PostMapping(value = "/pharmacy-creation-requests", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('PHARMACIST')")
    @Operation(
            summary = "Submit a pharmacy creation request",
            description = "Pharmacist submits a new pharmacy registration request along with a license document. "
                    + "An admin will review and approve or reject the request.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Request submitted successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Pharmacist already assigned, missing fields, or invalid document"
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
    public ResponseEntity<ApiResponse<PharmacyCreationRequestResponse>> createRequest(
            @Parameter(description = "Pharmacy name", example = "My Pharmacy", required = true)
            @RequestParam("name") String name,
            @Parameter(description = "Latitude coordinate", example = "30.0444", required = true)
            @RequestParam("latitude") Double latitude,
            @Parameter(description = "Longitude coordinate", example = "31.2357", required = true)
            @RequestParam("longitude") Double longitude,
            @Parameter(description = "Street address")
            @RequestParam(value = "address", required = false) String address,
            @Parameter(description = "Contact phone number", example = "+201234567890")
            @RequestParam(value = "phoneNumber", required = false) String phoneNumber,
            @Parameter(description = "Government-issued pharmacy license number", example = "LIC-12345", required = true)
            @RequestParam("licenseNumber") String licenseNumber,
            @Parameter(description = "Upload the license document (PDF or image)", required = true)
            @RequestParam("licenseDocument") MultipartFile licenseDocument
    ) {
        PharmacyCreationRequestResponse response = pharmacyCreationRequestService.createRequest(
                name, latitude, longitude, address, phoneNumber, licenseNumber, licenseDocument
        );
        return ResponseEntity.ok(ApiResponse.success("Pharmacy creation request submitted", response));
    }

    @GetMapping("/admin/pharmacy-creation-requests")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get all creation requests",
            description = "Admin only. Returns all pharmacy creation requests regardless of status.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Requests fetched successfully"
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
    public ResponseEntity<ApiResponse<List<PharmacyCreationRequestResponse>>> getAllRequests() {
        return ResponseEntity.ok(ApiResponse.success("Pharmacy creation requests fetched",
                pharmacyCreationRequestService.getAllRequests()));
    }

    @GetMapping("/admin/pharmacy-creation-requests/pending")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get pending creation requests",
            description = "Admin only. Returns all pharmacy creation requests that are pending review.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Pending requests fetched successfully"
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
    public ResponseEntity<ApiResponse<List<PharmacyCreationRequestResponse>>> getPendingRequests() {
        return ResponseEntity.ok(ApiResponse.success("Pending pharmacy creation requests fetched",
                pharmacyCreationRequestService.getPendingRequests()));
    }

    @GetMapping("/admin/pharmacy-creation-requests/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get creation request by ID",
            description = "Admin only. Returns a single pharmacy creation request with full details.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Request fetched successfully"
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
                    description = "Request not found"
            )
    })
    public ResponseEntity<ApiResponse<PharmacyCreationRequestResponse>> getRequestById(
            @Parameter(description = "Request ID", example = "1", required = true)
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success("Pharmacy creation request fetched",
                pharmacyCreationRequestService.getRequestById(id)));
    }

    @PostMapping("/admin/pharmacy-creation-requests/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Approve a creation request",
            description = "Admin only. Approves a pending pharmacy creation request. "
                    + "A new pharmacy is created and the requesting pharmacist becomes its admin.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Request approved; pharmacy created"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Request is no longer pending"
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
                    description = "Request not found"
            )
    })
    public ResponseEntity<ApiResponse<PharmacyCreationRequestResponse>> approveRequest(
            @Parameter(description = "Request ID", example = "1", required = true)
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success("Pharmacy creation request approved",
                pharmacyCreationRequestService.approve(id)));
    }

    @PostMapping("/admin/pharmacy-creation-requests/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Reject a creation request",
            description = "Admin only. Rejects a pending pharmacy creation request.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Request rejected"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Request is no longer pending"
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
                    description = "Request not found"
            )
    })
    public ResponseEntity<ApiResponse<PharmacyCreationRequestResponse>> rejectRequest(
            @Parameter(description = "Request ID", example = "1", required = true)
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success("Pharmacy creation request rejected",
                pharmacyCreationRequestService.reject(id)));
    }
}
