package com.example.dawanow.controller;

import com.example.dawanow.dtos.response.ApiResponse;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.dtos.response.PharmacyOfferResponse;
import com.example.dawanow.service.PharmacyOfferService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/offers")
@RequiredArgsConstructor
public class PharmacyOfferController {

    private final PharmacyOfferService pharmacyOfferService;

    @GetMapping("/pharmacy/{pharmacyId}")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PharmacyOfferResponse>>> getPharmacyOffers(
            @PathVariable Long pharmacyId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("Offers fetched", pharmacyOfferService.getOffersByPharmacy(pharmacyId, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'PHARMACIST', 'ADMIN')")
    public ResponseEntity<ApiResponse<PharmacyOfferResponse>> getOfferById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Offer fetched", pharmacyOfferService.getOfferById(id)));
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('PHARMACIST')")
    public ResponseEntity<ApiResponse<PharmacyOfferResponse>> acceptOffer(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Offer accepted", pharmacyOfferService.acceptOffer(id)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('PHARMACIST')")
    public ResponseEntity<ApiResponse<PharmacyOfferResponse>> rejectOffer(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Offer rejected", pharmacyOfferService.rejectOffer(id)));
    }
}
