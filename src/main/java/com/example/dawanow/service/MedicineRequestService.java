package com.example.dawanow.service;

import com.example.dawanow.dtos.request.CreateMedicineRequestRequest;
import com.example.dawanow.dtos.request.UpdateMedicineRequestStatusRequest;
import com.example.dawanow.dtos.response.MedicineRequestResponse;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.entity.Customer;
import com.example.dawanow.entity.MedicineRequest;
import com.example.dawanow.entity.Pharmacist;
import com.example.dawanow.entity.Pharmacy;
import com.example.dawanow.entity.Product;
import com.example.dawanow.entity.RequestItem;
import com.example.dawanow.entity.RequestStatus;
import com.example.dawanow.entity.User;
import com.example.dawanow.entity.UserRole;
import com.example.dawanow.exception.ResourceNotFoundException;
import com.example.dawanow.mapper.MedicineRequestMapper;
import com.example.dawanow.repo.MedicineRequestRepository;
import com.example.dawanow.repo.PharmacyRepository;
import com.example.dawanow.repo.ProductRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional
public class MedicineRequestService {

    private final MedicineRequestRepository medicineRequestRepository;
    private final PharmacyRepository pharmacyRepository;
    private final CurrentUserProvider currentUserProvider;
    private final MedicineRequestMapper medicineRequestMapper;

    public MedicineRequestResponse createRequest(CreateMedicineRequestRequest request) {
        return null;
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<MedicineRequestResponse> getCurrentCustomerRequests(Pageable pageable) {
        Customer currentCustomer = requireCurrentCustomer();
        return PaginatedResponse.from(
                medicineRequestRepository.findByCustomerId(currentCustomer.getId(), pageable)
                        .map(medicineRequestMapper::toResponse)
        );
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<MedicineRequestResponse> getAllRequests(Pageable pageable) {
        User currentUser = currentUserProvider.get();
        if (!isApplicationAdmin(currentUser)) {
            throw new AccessDeniedException("Only application administrators can view all medicine requests");
        }

        return PaginatedResponse.from(
                medicineRequestRepository.findAll(pageable).map(medicineRequestMapper::toResponse)
        );
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<MedicineRequestResponse> getPharmacyRequests(Long pharmacyId, Pageable pageable) {
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy not found"));
        requireCurrentPharmacistForPharmacy(pharmacy);

        return PaginatedResponse.from(
                medicineRequestRepository.findDistinctByOffers_Pharmacy_Id(pharmacyId, pageable)
                        .map(medicineRequestMapper::toResponse)
        );
    }

    @Transactional(readOnly = true)
    public MedicineRequestResponse getRequestById(Long id) {
        MedicineRequest medicineRequest = findRequest(id);
        User currentUser = currentUserProvider.get();

        boolean ownsRequest = currentUser instanceof Customer
                && medicineRequest.getCustomer().getId().equals(currentUser.getId());
        boolean pharmacyReceivedRequest = currentUser instanceof Pharmacist pharmacist
                && pharmacist.getPharmacy() != null
                && medicineRequestRepository.existsByIdAndOffers_Pharmacy_Id(
                        medicineRequest.getId(),
                        pharmacist.getPharmacy().getId()
                );
        if (!isApplicationAdmin(currentUser) && !ownsRequest && !pharmacyReceivedRequest) {
            throw new AccessDeniedException("You are not allowed to view this medicine request");
        }

        return medicineRequestMapper.toResponse(medicineRequest);
    }

    public MedicineRequestResponse updateRequestStatus(Long id, UpdateMedicineRequestStatusRequest request) {
        MedicineRequest medicineRequest = findRequest(id);
        User currentUser = currentUserProvider.get();
        RequestStatus targetStatus = request.status();

        if (targetStatus == null) {
            throw new IllegalArgumentException("Request status is required");
        }
        if (!isApplicationAdmin(currentUser)) {
            boolean ownsRequest = currentUser instanceof Customer
                    && medicineRequest.getCustomer().getId().equals(currentUser.getId());
            if (!ownsRequest) {
                throw new AccessDeniedException("You are not allowed to update this medicine request");
            }
            if (targetStatus != RequestStatus.CANCELLED) {
                throw new AccessDeniedException("Customers can only cancel their medicine requests");
            }
            if (medicineRequest.getStatus() != RequestStatus.PENDING) {
                throw new IllegalArgumentException("Only pending medicine requests can be cancelled");
            }
        }

        medicineRequest.setStatus(targetStatus);
        return medicineRequestMapper.toResponse(medicineRequest);
    }

    private MedicineRequest findRequest(Long id) {
        return medicineRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine request not found"));
    }

    private Customer requireCurrentCustomer() {
        User currentUser = currentUserProvider.get();
        if (!(currentUser instanceof Customer customer)) {
            throw new AccessDeniedException("A customer account is required");
        }
        return customer;
    }

    private Pharmacist requireCurrentPharmacistForPharmacy(Pharmacy pharmacy) {
        User currentUser = currentUserProvider.get();
        if (!(currentUser instanceof Pharmacist pharmacist)
                || pharmacist.getPharmacy() == null
                || !pharmacy.getId().equals(pharmacist.getPharmacy().getId())) {
            throw new AccessDeniedException("The pharmacist must belong to the requested pharmacy");
        }
        return pharmacist;
    }

    private boolean isApplicationAdmin(User user) {
        return user.getRole() == UserRole.ADMIN;
    }
}
