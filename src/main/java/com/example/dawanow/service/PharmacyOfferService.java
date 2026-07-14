package com.example.dawanow.service;

import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.dtos.response.PharmacyOfferResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class PharmacyOfferService {

    public PaginatedResponse<PharmacyOfferResponse> getOffersByPharmacy(Long pharmacyId, Pageable pageable) {
        return PaginatedResponse.empty(pageable);
    }

    public PharmacyOfferResponse getOfferById(Long id) {
        return null;
    }

    public PharmacyOfferResponse acceptOffer(Long id) {
        return null;
    }

    public PharmacyOfferResponse rejectOffer(Long id) {
        return null;
    }
}
