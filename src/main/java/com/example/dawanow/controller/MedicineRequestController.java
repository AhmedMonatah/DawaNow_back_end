package com.example.dawanow.controller;

import com.example.dawanow.dtos.request.CreateMedicineRequestRequest;
import com.example.dawanow.dtos.request.ConfirmSelectionRequest;
import com.example.dawanow.dtos.request.UpdateMedicineRequestStatusRequest;
import com.example.dawanow.dtos.response.ApiResponse;
import com.example.dawanow.dtos.response.ConfirmationResponse;
import com.example.dawanow.dtos.response.MedicineRequestResponse;
import com.example.dawanow.dtos.response.MedicineRequestResultResponse;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.service.FileStorageService;
import com.example.dawanow.service.MedicineRequestService;
import com.example.dawanow.service.MedicineRequestConfirmationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/requests")
@RequiredArgsConstructor
@Tag(name = "Medicine Requests", description = "Customers submit medicine requests and track their status")
public class MedicineRequestController {

    private final MedicineRequestService medicineRequestService;
    private final FileStorageService fileStorageService;
    private final MedicineRequestConfirmationService medicineRequestConfirmationService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            summary = "Create a medicine request",
            description = "Customer only. Submits a new medicine request and an optional prescription image (JPEG or PNG).",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Medicine request created successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed or cart is empty"
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
                    responseCode = "413",
                    description = "Uploaded prescription image exceeds the maximum allowed size"
            )
    })
    public ResponseEntity<ApiResponse<MedicineRequestResponse>> createRequest(
            @Parameter(
                    description = "Medicine request payload containing delivery address and coordinates",
                    required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
            )
            @RequestPart("request") CreateMedicineRequestRequest request,

            @Parameter(
                    description = "Optional prescription file (JPEG, PNG, PDF)",
                    required = false,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(type = "string", format = "binary"))
            )
            @RequestPart(value = "prescription", required = false) MultipartFile prescription
    ) {
        MedicineRequestResponse medicineRequestResponse = medicineRequestService.createRequest(request, prescription);

        return ResponseEntity.ok(ApiResponse.success("Medicine request created", medicineRequestResponse));
    }

    @PostMapping("/{requestId}/confirm")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            summary = "Confirm selected offer items and create optimized pharmacy orders",
            description = "Customer only. Chooses the minimum number of pharmacies that can fulfill every requested "
                    + "item, then creates one order per selected pharmacy.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    public ResponseEntity<ApiResponse<ConfirmationResponse>> confirmSelection(
            @PathVariable Long requestId,
            @Valid @RequestBody ConfirmSelectionRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Selection confirmed and orders created",
                medicineRequestConfirmationService.confirm(requestId, request)
        ));
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            summary = "Get my medicine requests",
            description = "Customer only. Returns paginated list of medicine requests for the currently authenticated customer.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Requests fetched successfully with pagination metadata"
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
    public ResponseEntity<ApiResponse<PaginatedResponse<MedicineRequestResponse>>> getCurrentCustomerRequests(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("Medicine requests fetched", medicineRequestService.getCurrentCustomerRequests(pageable)));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get all medicine requests",
            description = "Admin only. Returns paginated list of all medicine requests in the system.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "All requests fetched successfully with pagination metadata"
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
    public ResponseEntity<ApiResponse<PaginatedResponse<MedicineRequestResponse>>> getAllRequests(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("Medicine requests fetched", medicineRequestService.getAllRequests(pageable)));
    }
    @GetMapping("/{requestId}/result")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            summary = "Get medicine request calculation result",
            description = "Customer only. Calculates and returns the total price and item availability/alternatives for a specified medicine request.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Medicine request result calculated and fetched successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied. Only customers can view medicine request results"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Medicine request not found"
            )
    })
    public ResponseEntity<ApiResponse<MedicineRequestResultResponse>> getRequestResult(
            @PathVariable Long requestId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Medicine request result fetched",
                medicineRequestService.getMedicineRequestResult(requestId)));
    }


    @GetMapping("/pharmacy/{pharmacyId}")
    @PreAuthorize("hasRole('PHARMACIST')")
    @Operation(
            summary = "Get requests sent to a pharmacy",
            description = "Pharmacist only. Returns requests associated with the specified pharmacy through pharmacy "
                    + "offers. The logged-in pharmacist must belong to that exact pharmacy.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Pharmacy requests fetched successfully with pagination metadata"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "The logged-in user is not a pharmacist tied to this pharmacy"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Pharmacy not found"
            )
    })
    public ResponseEntity<ApiResponse<PaginatedResponse<MedicineRequestResponse>>> getPharmacyRequests(
            @Parameter(description = "Pharmacy ID", example = "1", required = true)
            @PathVariable Long pharmacyId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Pharmacy medicine requests fetched",
                medicineRequestService.getPharmacyRequests(pharmacyId, pageable)
        ));
    }



    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'PHARMACIST', 'ADMIN')")
    @Operation(
            summary = "Get medicine request by ID",
            description = "Returns one medicine request to its customer owner, an application admin, or a pharmacist "
                    + "whose pharmacy received that request.",
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
                    description = "Customer, pharmacist, or administrator role is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Request not found"
            )
    })
    public ResponseEntity<ApiResponse<MedicineRequestResponse>> getRequestById(
            @Parameter(description = "Request ID", example = "1", required = true)
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success("Medicine request fetched", medicineRequestService.getRequestById(id)));
    }

//    @PutMapping("/{id}/status")
//    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
//    @Operation(
//            summary = "Update medicine request status",
//            description = "Application admins can update any request status. A customer can update only their own "
//                    + "pending request and can change it only to CANCELLED.",
//            security = @SecurityRequirement(name = "basicAuth")
//    )
//    @ApiResponses({
//            @io.swagger.v3.oas.annotations.responses.ApiResponse(
//                    responseCode = "200",
//                    useReturnTypeSchema = true,
//                    description = "Request status updated successfully"
//            ),
//            @io.swagger.v3.oas.annotations.responses.ApiResponse(
//                    responseCode = "400",
//                    description = "The requested status transition is invalid"
//            ),
//            @io.swagger.v3.oas.annotations.responses.ApiResponse(
//                    responseCode = "401",
//                    description = "Authentication is required"
//            ),
//            @io.swagger.v3.oas.annotations.responses.ApiResponse(
//                    responseCode = "403",
//                    description = "The current user is not allowed to update this request"
//            ),
//            @io.swagger.v3.oas.annotations.responses.ApiResponse(
//                    responseCode = "404",
//                    description = "Medicine request not found"
//            )
//    })
//    public ResponseEntity<ApiResponse<MedicineRequestResponse>> updateRequestStatus(
//            @Parameter(description = "Medicine request ID", example = "1", required = true)
//            @PathVariable Long id,
//            @Valid @RequestBody UpdateMedicineRequestStatusRequest request
//    ) {
//        return ResponseEntity.ok(ApiResponse.success(
//                "Medicine request status updated",
//                medicineRequestService.updateRequestStatus(id, request)
//        ));
//    }
}
