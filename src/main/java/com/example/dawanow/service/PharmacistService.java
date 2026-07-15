package com.example.dawanow.service;

import com.example.dawanow.dtos.request.AssignPharmacistToPharmacyRequest;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.dtos.response.PharmacistResponse;
import com.example.dawanow.entity.Pharmacist;
import com.example.dawanow.entity.Pharmacy;
import com.example.dawanow.exception.ResourceNotFoundException;
import com.example.dawanow.mapper.PharmacistMapper;
import com.example.dawanow.repo.PharmacistRepository;
import com.example.dawanow.repo.PharmacyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PharmacistService {
    private final PharmacistRepository pharmacistRepository;
    private final PharmacyRepository pharmacyRepository;
    private final CurrentPharmacistProvider currentPharmacistProvider;
    private final PharmacistMapper pharmacistMapper;

    @Transactional(readOnly = true)
    public PharmacistResponse getCurrentPharmacist() {
        return pharmacistMapper.toResponse(currentPharmacistProvider.get());
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<PharmacistResponse> getAllPharmacists(Pageable pageable) {
        return PaginatedResponse.from(pharmacistRepository.findAll(pageable).map(pharmacistMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PharmacistResponse getPharmacistById(Long id) {
        return pharmacistMapper.toResponse(findPharmacist(id));
    }

    public PharmacistResponse assignToPharmacy(Long id, AssignPharmacistToPharmacyRequest request) {
        Pharmacist pharmacist = findPharmacist(id);
        if (pharmacist.getPharmacy() != null || pharmacyRepository.existsByAdminPharmacistId(pharmacist.getId())) {
            throw new IllegalArgumentException("A pharmacist can belong to only one pharmacy");
        }
        Pharmacy pharmacy = pharmacyRepository.findById(request.pharmacyId())
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy not found"));
        pharmacist.setPharmacy(pharmacy);
        return pharmacistMapper.toResponse(pharmacist);
    }

    public void removeCurrentPharmacistFromPharmacy(Long pharmacyId) {
        Pharmacist pharmacist = currentPharmacistProvider.get();
        if (pharmacist.getPharmacy() == null || !pharmacist.getPharmacy().getId().equals(pharmacyId)) {
            throw new IllegalArgumentException("You are not assigned to this pharmacy");
        }
        if (pharmacist.getPharmacy().getAdminPharmacist().getId().equals(pharmacist.getId())) {
            throw new IllegalArgumentException("The pharmacy admin must transfer administration before leaving");
        }
        pharmacist.setPharmacy(null);
    }

    public void removePharmacistFromPharmacy(Long pharmacyId, Long pharmacistId) {
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy not found"));
        Pharmacist current = currentPharmacistProvider.get();
        if (!pharmacy.getAdminPharmacist().getId().equals(current.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Only this pharmacy's pharmacist admin can remove members");
        }
        Pharmacist pharmacist = findPharmacist(pharmacistId);
        if (pharmacist.getPharmacy() == null || !pharmacist.getPharmacy().getId().equals(pharmacyId)) {
            throw new IllegalArgumentException("Pharmacist is not assigned to this pharmacy");
        }
        if (pharmacist.getId().equals(pharmacy.getAdminPharmacist().getId())) {
            throw new IllegalArgumentException("The pharmacy admin cannot be removed");
        }
        pharmacist.setPharmacy(null);
    }

    private Pharmacist findPharmacist(Long id) {
        return pharmacistRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Pharmacist not found"));
    }

}
