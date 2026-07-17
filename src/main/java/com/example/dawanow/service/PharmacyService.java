package com.example.dawanow.service;

import com.example.dawanow.dtos.request.CreatePharmacyRequest;
import com.example.dawanow.dtos.request.UpdatePharmacyRequest;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.dtos.response.PharmacyMineResponse;
import com.example.dawanow.dtos.response.PharmacyResponse;
import com.example.dawanow.entity.UserRole;
import com.example.dawanow.entity.Pharmacist;
import com.example.dawanow.entity.Pharmacy;
import com.example.dawanow.entity.User;
import com.example.dawanow.exception.ResourceNotFoundException;
import com.example.dawanow.mapper.PharmacyMapper;
import com.example.dawanow.repo.PharmacyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PharmacyService {
    private final PharmacyRepository pharmacyRepository;
    private final CurrentPharmacistProvider currentPharmacistProvider;
    private final CurrentUserProvider currentUserProvider;
    private final PharmacyMapper pharmacyMapper;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public PaginatedResponse<PharmacyResponse> getAllPharmacies(Pageable pageable) {
        return PaginatedResponse.from(pharmacyRepository.findAll(pageable).map(pharmacyMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PharmacyResponse getPharmacyById(Long id) {
        return pharmacyMapper.toResponse(findPharmacy(id));
    }

    @Transactional(readOnly = true)
    public PharmacyMineResponse getMyPharmacy() {
        Pharmacist pharmacist = currentPharmacistProvider.get();
        Pharmacy pharmacy = pharmacist.getPharmacy();
        if (pharmacy == null) {
            throw new ResourceNotFoundException("You are not assigned to any pharmacy");
        }
        return PharmacyMineResponse.from(pharmacy, pharmacist);
    }

    @Transactional
    public PharmacyResponse createPharmacy(CreatePharmacyRequest pharmacyRequest, MultipartFile licenseFile ) {
        Pharmacist pharmacyAdmin = currentPharmacistProvider.get();
        ensureNotAssignedToAnyPharmacy(pharmacyAdmin);

        String storedPath = fileStorageService.storeLicenseFile(licenseFile);
        log.info("pharmacist {} uploaded license file to {}", pharmacyAdmin.getId(), storedPath);
        Pharmacy pharmacy = new Pharmacy();
        pharmacy.setName(requireText(pharmacyRequest.name(), "Pharmacy name"));
        pharmacy.setLatitude(pharmacyRequest.latitude());
        pharmacy.setLongitude(pharmacyRequest.longitude());
        pharmacy.setAddress(trimToNull(pharmacyRequest.address()));
        pharmacy.setPhoneNumber(trimToNull(pharmacyRequest.phoneNumber()));
        pharmacy.setAdminPharmacist(pharmacyAdmin);
        pharmacy.setLicenseDocumentPath(storedPath);
        Pharmacy saved = pharmacyRepository.save(pharmacy);

        pharmacyAdmin.setPharmacy(saved);
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

    @Transactional
    public void deletePharmacy(Long id) {
        Pharmacy pharmacy = findPharmacy(id);
        User current = currentUserProvider.get();
        boolean isPlatformAdmin = current.getRole() == UserRole.ADMIN;
        if (!isPlatformAdmin) {
            requireCurrentAdmin(pharmacy);
        }
        pharmacy.getPharmacists().forEach(p -> p.setPharmacy(null));
        pharmacy.getPharmacists().clear();
        pharmacyRepository.delete(pharmacy);
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
