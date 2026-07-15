package com.example.dawanow.service;

import com.example.dawanow.dtos.response.PharmacyCreationRequestResponse;
import com.example.dawanow.entity.Pharmacist;
import com.example.dawanow.entity.Pharmacy;
import com.example.dawanow.entity.PharmacyCreationRequest;
import com.example.dawanow.entity.PharmacyCreationRequestStatus;
import com.example.dawanow.entity.User;
import com.example.dawanow.exception.ResourceNotFoundException;
import com.example.dawanow.mapper.PharmacyCreationRequestMapper;
import com.example.dawanow.repo.PharmacyCreationRequestRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional
public class PharmacyCreationRequestService {

    private final PharmacyCreationRequestRepository requestRepository;
    private final PharmacyService pharmacyService;
    private final CurrentPharmacistProvider currentPharmacistProvider;
    private final PharmacyCreationRequestMapper requestMapper;

    @Value("${dawanow.upload.license-dir:uploads/licenses}")
    private String licenseUploadDir;

    public PharmacyCreationRequestResponse createRequest(
            String name, Double latitude, Double longitude,
            String address, String phoneNumber,
            String licenseNumber, MultipartFile licenseDocument
    ) {
        Pharmacist pharmacist = currentPharmacistProvider.get();
        pharmacyService.ensureNotAssignedToAnyPharmacy(pharmacist);

        if (!StringUtils.hasText(name)) throw new IllegalArgumentException("Pharmacy name cannot be blank");
        if (licenseNumber == null || licenseNumber.isBlank()) throw new IllegalArgumentException("License number cannot be blank");
        if (licenseDocument == null || licenseDocument.isEmpty()) throw new IllegalArgumentException("License document is required");

        String documentPath = storeLicenseDocument(licenseDocument);

        PharmacyCreationRequest request = new PharmacyCreationRequest();
        request.setPharmacist(pharmacist);
        request.setName(name.trim());
        request.setLatitude(latitude);
        request.setLongitude(longitude);
        request.setAddress(address != null ? address.trim() : null);
        request.setPhoneNumber(phoneNumber != null ? phoneNumber.trim() : null);
        request.setLicenseNumber(licenseNumber.trim());
        request.setLicenseDocumentPath(documentPath);
        request.setStatus(PharmacyCreationRequestStatus.PENDING);
        request.setCreatedAt(Instant.now());
        return requestMapper.toResponse(requestRepository.save(request));
    }

    @Transactional(readOnly = true)
    public List<PharmacyCreationRequestResponse> getPendingRequests() {
        return requestRepository.findByStatus(PharmacyCreationRequestStatus.PENDING)
                .stream().map(requestMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PharmacyCreationRequestResponse> getAllRequests() {
        return requestRepository.findAll()
                .stream().map(requestMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PharmacyCreationRequestResponse getRequestById(Long id) {
        return requestMapper.toResponse(findRequest(id));
    }

    public PharmacyCreationRequestResponse approve(Long requestId) {
        PharmacyCreationRequest request = findRequest(requestId);
        if (request.getStatus() != PharmacyCreationRequestStatus.PENDING) {
            throw new IllegalArgumentException("Only a pending request can be approved");
        }

        Pharmacist pharmacist = request.getPharmacist();
        pharmacyService.ensureNotAssignedToAnyPharmacy(pharmacist);

        Pharmacy pharmacy = new Pharmacy();
        pharmacy.setName(request.getName());
        pharmacy.setLatitude(request.getLatitude());
        pharmacy.setLongitude(request.getLongitude());
        pharmacy.setAddress(request.getAddress());
        pharmacy.setPhoneNumber(request.getPhoneNumber());
        pharmacy.setLicenseNumber(request.getLicenseNumber());
        pharmacy.setLicenseDocumentPath(request.getLicenseDocumentPath());
        pharmacy.setAdminPharmacist(pharmacist);

        pharmacy = pharmacyService.savePharmacy(pharmacy);

        pharmacist.setPharmacy(pharmacy);

        User admin = currentPharmacistProvider.getCurrentUser();
        request.setStatus(PharmacyCreationRequestStatus.APPROVED);
        request.setReviewedBy(admin);
        request.setApprovedPharmacy(pharmacy);
        request.setUpdatedAt(Instant.now());
        return requestMapper.toResponse(request);
    }

    public PharmacyCreationRequestResponse reject(Long requestId) {
        PharmacyCreationRequest request = findRequest(requestId);
        if (request.getStatus() != PharmacyCreationRequestStatus.PENDING) {
            throw new IllegalArgumentException("Only a pending request can be rejected");
        }
        User admin = currentPharmacistProvider.getCurrentUser();
        request.setStatus(PharmacyCreationRequestStatus.REJECTED);
        request.setReviewedBy(admin);
        request.setUpdatedAt(Instant.now());
        return requestMapper.toResponse(request);
    }

    private PharmacyCreationRequest findRequest(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy creation request not found"));
    }

    private String storeLicenseDocument(MultipartFile file) {
        try {
            Path uploadDir = Paths.get(licenseUploadDir);
            Files.createDirectories(uploadDir);
            String extension = "";
            String originalName = file.getOriginalFilename();
            if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf("."));
            }
            String filename = UUID.randomUUID().toString() + extension;
            Path targetPath = uploadDir.resolve(filename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            return targetPath.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to store license document", e);
        }
    }

}
