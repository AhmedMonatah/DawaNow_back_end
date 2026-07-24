package com.example.dawanow.service;

import com.example.dawanow.dtos.request.CreateMedicineRequestRequest;
import com.example.dawanow.dtos.response.MedicineRequestResponse;
import com.example.dawanow.dtos.response.MedicineRequestResultItemResponse;
import com.example.dawanow.dtos.response.MedicineRequestResultResponse;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.entity.*;
import com.example.dawanow.exception.ResourceNotFoundException;
import com.example.dawanow.mapper.MedicineRequestMapper;
import com.example.dawanow.mapper.MedicineRequestResultItemMapper;
import com.example.dawanow.mapper.MedicineRequestResultMapper;
import com.example.dawanow.repo.MedicineRequestRepository;
import com.example.dawanow.repo.PharmacyAssignmentRepository;
import com.example.dawanow.repo.PharmacyOfferItemRepository;
import com.example.dawanow.repo.PharmacyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class MedicineRequestService {

    private final MedicineRequestRepository medicineRequestRepository;
    private final PharmacyRepository pharmacyRepository;
    private final CurrentUserProvider currentUserProvider;
    private final MedicineRequestMapper medicineRequestMapper;
    private final CartService cartService;
    private final AssignmentService assignmentService;
    private final PharmacyAssignmentRepository pharmacyAssignmentRepository;
    private final FileStorageService fileStorageService;
    private final ProductService productService;
    private final PharmacyOfferItemRepository pharmacyOfferItemRepository;
    private final MedicineRequestResultItemMapper medicineRequestResultItemMapper;

    @Value("${dawanow.request.search-timeout-minutes:15}")
    private long searchTimeoutMinutes;

    @Transactional
    public MedicineRequestResponse createRequest(CreateMedicineRequestRequest request,
                                                 MultipartFile prescription) {
        Customer customer = (Customer)currentUserProvider.get();
        Cart cart = cartService.getCartEntity();


        if (cart.getItems().isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }
        MedicineRequest medicineRequest = new MedicineRequest();

        medicineRequest.setCustomer(customer);

        medicineRequest.setDeliveryLatitude(request.deliveryLatitude());

        medicineRequest.setDeliveryLongitude(request.deliveryLongitude());

        medicineRequest.setDeliveryAddress(request.deliveryAddress());



        if (prescription != null && !prescription.isEmpty()) {
            String url = fileStorageService.storePrescription(prescription);
            medicineRequest.setPrescriptionUrl(url);

        }

        medicineRequest.setCreatedAt(LocalDateTime.now());
        medicineRequest.setExpiresAt(LocalDateTime.now().plusMinutes(searchTimeoutMinutes));


        for(CartItem cartItem :  cart.getItems()) {
            RequestItem requestItem = new RequestItem();
            requestItem.setProduct(cartItem.getProduct());
            requestItem.setQuantity(cartItem.getQuantity());
            requestItem.setRequest(medicineRequest);
            medicineRequest.getItems().add(requestItem);
        }

        medicineRequest.setStatus(RequestStatus.PENDING);
        medicineRequestRepository.save(medicineRequest);

        assignmentService.assignNearbyPharmacies(medicineRequest);

//        medicineRequest.setStatus(RequestStatus.SEARCHING);

        cartService.clearCart();

        return medicineRequestMapper.toResponse(medicineRequest);
    }

    public MedicineRequest getEntity(Long medicineRequestId){
        return medicineRequestRepository.findById(medicineRequestId).orElseThrow(()->new ResourceNotFoundException("Medicine Request not found"));
    }

    @Transactional
    public PaginatedResponse<MedicineRequestResponse> getCurrentPharmacyRequests(Pageable pageable) {

        Pharmacist pharmacist = (Pharmacist) currentUserProvider.get();

        if (pharmacist.getPharmacy() == null) {
            throw new ResourceNotFoundException("Current pharmacist is not assigned to any pharmacy");
        }

        Long pharmacyId = pharmacist.getPharmacy().getId();



        return PaginatedResponse.from(
                pharmacyAssignmentRepository.getPharmacyAssignmentsByPharmacy_Id(pharmacyId,pageable)
                        .map(pharmacyAssignment -> medicineRequestMapper.toResponse(pharmacyAssignment.getMedicineRequest()))
        );
    }

    @Scheduled(fixedRate = 60000)
    public void expireRequests() {
        List<MedicineRequest> medicineRequestList =  medicineRequestRepository.findByStatusAndExpiresAtBefore(RequestStatus.PENDING, LocalDateTime.now());
        for (MedicineRequest medicineRequest : medicineRequestList) {
            medicineRequest.setStatus(RequestStatus.EXPIRED);
        }
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

    @Transactional
    public MedicineRequestResultResponse getMedicineRequestResult(Long medicineRequestId){
        MedicineRequest medicineRequest = medicineRequestRepository.findById(medicineRequestId).orElseThrow(()->new ResourceNotFoundException("Medicine Request not found"));
        List<MedicineRequestResultItemResponse> medicineRequestResultItemResponseList = new ArrayList<>();

        BigDecimal totalPrice = BigDecimal.ZERO;

        List<RequestItem> requestItems= medicineRequest.getItems();
        List<PharmacyOffer> pharmacyOffers = medicineRequest.getOffers();

        Map<Long, PharmacyOfferItem> bestOfferItems = new HashMap<>();

        for (PharmacyOffer offer : pharmacyOffers) {
            for (PharmacyOfferItem item : offer.getItems()) {

                PharmacyOfferItem current =
                        bestOfferItems.get(item.getRequestItem().getId());

                if (current == null
                        || (current.isAlternative() && !item.isAlternative())) {

                    bestOfferItems.put(item.getRequestItem().getId(), item);
                }
            }
        }

        for(RequestItem requestItem : requestItems){
           PharmacyOfferItem bestOffer = bestOfferItems.get(requestItem.getId());
            MedicineRequestResultItemResponse medicineRequestResultItemResponse;
            if(bestOffer == null){
                medicineRequestResultItemResponse = MedicineRequestResultMapper.unavailable(requestItem.getProduct().getProductName(), requestItem.getId());
            }
            else{
               medicineRequestResultItemResponse = medicineRequestResultItemMapper.toResponse(bestOffer);
               totalPrice = totalPrice.add(bestOffer.getProduct().getPrice().multiply(BigDecimal.valueOf(requestItem.getQuantity())));
            }
             medicineRequestResultItemResponseList.add(medicineRequestResultItemResponse);
        }
        return new MedicineRequestResultResponse(medicineRequestResultItemResponseList, totalPrice);
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


//
//    public MedicineRequestResponse updateRequestStatus(Long id, UpdateMedicineRequestStatusRequest request) {
//        MedicineRequest medicineRequest = findRequest(id);
//        User currentUser = currentUserProvider.get();
//        RequestStatus targetStatus = request.status();
//
//        if (targetStatus == null) {
//            throw new IllegalArgumentException("Request status is required");
//        }
//        if (!isApplicationAdmin(currentUser)) {
//            boolean ownsRequest = currentUser instanceof Customer
//                    && medicineRequest.getCustomer().getId().equals(currentUser.getId());
//            if (!ownsRequest) {
//                throw new AccessDeniedException("You are not allowed to update this medicine request");
//            }
//            if (targetStatus != RequestStatus.CANCELLED) {
//                throw new AccessDeniedException("Customers can only cancel their medicine requests");
//            }
//            if (medicineRequest.getStatus() != RequestStatus.PENDING) {
//                throw new IllegalArgumentException("Only pending medicine requests can be cancelled");
//            }
//        }
//
//        medicineRequest.setStatus(targetStatus);
//        return medicineRequestMapper.toResponse(medicineRequest);
//    }

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
