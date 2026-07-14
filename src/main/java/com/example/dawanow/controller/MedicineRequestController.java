package com.example.dawanow.controller;

import com.example.dawanow.dtos.request.CreateMedicineRequestRequest;
import com.example.dawanow.dtos.response.ApiResponse;
import com.example.dawanow.dtos.response.MedicineRequestResponse;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.service.MedicineRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/requests")
@RequiredArgsConstructor
public class MedicineRequestController {

    private final MedicineRequestService medicineRequestService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<MedicineRequestResponse>> createRequest(
            @Valid @RequestBody CreateMedicineRequestRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Medicine request created", medicineRequestService.createRequest(request)));
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<PaginatedResponse<MedicineRequestResponse>>> getCurrentCustomerRequests(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("Medicine requests fetched", medicineRequestService.getCurrentCustomerRequests(pageable)));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<MedicineRequestResponse>>> getAllRequests(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("Medicine requests fetched", medicineRequestService.getAllRequests(pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'PHARMACIST', 'ADMIN')")
    public ResponseEntity<ApiResponse<MedicineRequestResponse>> getRequestById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Medicine request fetched", medicineRequestService.getRequestById(id)));
    }
}
