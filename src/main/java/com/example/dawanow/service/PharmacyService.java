package com.example.dawanow.service;

import com.example.dawanow.dtos.request.CreatePharmacyRequest;
import com.example.dawanow.dtos.request.UpdatePharmacyRequest;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.dtos.response.PharmacyResponse;
import com.example.dawanow.entity.Pharmacist;
import com.example.dawanow.entity.Pharmacy;
import com.example.dawanow.exception.ResourceNotFoundException;
import com.example.dawanow.mapper.PharmacyMapper;
import com.example.dawanow.repo.PharmacyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional
public class PharmacyService {
    private final PharmacyRepository pharmacyRepository;
    private final CurrentPharmacistProvider currentPharmacistProvider;
    private final PharmacyMapper pharmacyMapper;

    @Transactional(readOnly = true)
    public PaginatedResponse<PharmacyResponse> getAllPharmacies(Pageable pageable) {
        return PaginatedResponse.from(pharmacyRepository.findAll(pageable).map(pharmacyMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PharmacyResponse getPharmacyById(Long id) {
        return pharmacyMapper.toResponse(findPharmacy(id));
    }

    public PharmacyResponse createPharmacy(CreatePharmacyRequest request) {
        Pharmacist pharmacist = currentPharmacistProvider.get();
        ensureNotAssignedToAnyPharmacy(pharmacist);

        Pharmacy pharmacy = new Pharmacy();
        pharmacy.setName(requireText(request.name(), "Pharmacy name"));
        pharmacy.setLatitude(request.latitude());
        pharmacy.setLongitude(request.longitude());
        pharmacy.setAddress(trimToNull(request.address()));
        pharmacy.setPhoneNumber(trimToNull(request.phoneNumber()));
        pharmacy.setAdminPharmacist(pharmacist);
        Pharmacy saved = pharmacyRepository.save(pharmacy);

        pharmacist.setPharmacy(saved);
        return pharmacyMapper.toResponse(saved);
    }

    public PharmacyResponse updatePharmacy(Long id, UpdatePharmacyRequest request) {
        Pharmacy pharmacy = findPharmacy(id);
        requireCurrentAdmin(pharmacy);
        if (request.name() != null) pharmacy.setName(requireText(request.name(), "Pharmacy name"));
        if (request.latitude() != null) pharmacy.setLatitude(request.latitude());
        if (request.longitude() != null) pharmacy.setLongitude(request.longitude());
        if (request.address() != null) pharmacy.setAddress(trimToNull(request.address()));
        if (request.phoneNumber() != null) pharmacy.setPhoneNumber(trimToNull(request.phoneNumber()));
        return pharmacyMapper.toResponse(pharmacy);
    }

    public void deletePharmacy(Long id) {
        Pharmacy pharmacy = findPharmacy(id);
        requireCurrentAdmin(pharmacy);
        throw new IllegalArgumentException("A pharmacy cannot be deleted while it has an admin; transfer administration or deactivate it instead");
    }

    Pharmacy findPharmacy(Long id) {
        return pharmacyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy not found"));
    }

    Pharmacist requireCurrentAdmin(Pharmacy pharmacy) {
        Pharmacist current = currentPharmacistProvider.get();
        if (!pharmacy.getAdminPharmacist().getId().equals(current.getId())) {
            throw new AccessDeniedException("Only this pharmacy's pharmacist admin can perform this action");
        }
        return current;
    }

    Pharmacy savePharmacy(Pharmacy pharmacy) {
        return pharmacyRepository.save(pharmacy);
    }

    void ensureNotAssignedToAnyPharmacy(Pharmacist pharmacist) {
        if (pharmacist.getPharmacy() != null || pharmacyRepository.existsByAdminPharmacistId(pharmacist.getId())) {
            throw new IllegalArgumentException("A pharmacist can belong to only one pharmacy");
        }
    }

    private String requireText(String value, String name) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException(name + " cannot be blank");
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
