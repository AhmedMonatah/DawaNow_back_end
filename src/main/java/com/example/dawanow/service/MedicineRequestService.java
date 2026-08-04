package com.example.dawanow.service;

import com.example.dawanow.dtos.request.CreateMedicineRequestRequest;
import com.example.dawanow.dtos.response.MedicineRequestItemResponse;
import com.example.dawanow.dtos.response.MedicineRequestResponse;
import com.example.dawanow.dtos.response.MedicineRequestResultItemResponse;
import com.example.dawanow.dtos.response.MedicineRequestResultResponse;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.dtos.response.ProductSummaryResponse;
import com.example.dawanow.entity.*;
import com.example.dawanow.exception.ResourceNotFoundException;
import com.example.dawanow.mapper.MedicineRequestMapper;
import com.example.dawanow.mapper.MedicineRequestResultItemMapper;
import com.example.dawanow.mapper.MedicineRequestResultMapper;
import com.example.dawanow.repo.MedicineRequestRepository;
import com.example.dawanow.repo.PharmacyAssignmentRepository;
import com.example.dawanow.repo.PharmacyOfferItemRepository;
import com.example.dawanow.repo.PharmacyRepository;
import com.example.dawanow.repo.ProductRepository;
import com.example.dawanow.repo.RequestItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class MedicineRequestService {

    private static final String DEFAULT_LANG = "en";
    private static final String ARABIC = "ar";

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
    private final RequestItemRepository requestItemRepository;
    private final ProductRepository productRepository;

    @Value("${dawanow.request.search-timeout-minutes:15}")
    private long searchTimeoutMinutes;

    @Transactional
    public MedicineRequestResponse createRequest(CreateMedicineRequestRequest request,
                                                 MultipartFile prescription,
                                                 String lang) {
        String language = normalizeLanguage(lang);
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

        medicineRequest.setNotes(request.notes());

        medicineRequest.setPaymentMethod(request.paymentMethod());

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

        return toResponse(medicineRequest, resolveItems(List.of(medicineRequest), language));
    }

    public MedicineRequest getEntity(Long medicineRequestId){
        return medicineRequestRepository.findById(medicineRequestId).orElseThrow(()->new ResourceNotFoundException("Medicine Request not found"));
    }

    @Transactional
    public PaginatedResponse<MedicineRequestResponse> getCurrentPharmacyRequests(String lang, Pageable pageable) {
        String language = normalizeLanguage(lang);

        Pharmacist pharmacist = (Pharmacist) currentUserProvider.get();

        if (pharmacist.getPharmacy() == null) {
            throw new ResourceNotFoundException("Current pharmacist is not assigned to any pharmacy");
        }

        Long pharmacyId = pharmacist.getPharmacy().getId();

        Page<PharmacyAssignment> assignments =
                pharmacyAssignmentRepository.getPharmacyAssignmentsByPharmacy_Id(pharmacyId, pageable);

        List<MedicineRequest> requests = assignments.getContent().stream()
                .map(PharmacyAssignment::getMedicineRequest)
                .toList();
        Map<Long, List<MedicineRequestItemResponse>> itemsByRequestId = resolveItems(requests, language);

        return PaginatedResponse.from(
                assignments.map(assignment -> toResponse(assignment.getMedicineRequest(), itemsByRequestId))
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
    public PaginatedResponse<MedicineRequestResponse> getCurrentCustomerRequests(String lang, Pageable pageable) {
        String language = normalizeLanguage(lang);
        Customer currentCustomer = requireCurrentCustomer();
        Page<MedicineRequest> requests =
                medicineRequestRepository.findByCustomerId(currentCustomer.getId(), pageable);
        Map<Long, List<MedicineRequestItemResponse>> itemsByRequestId = resolveItems(requests.getContent(), language);
        return PaginatedResponse.from(
                requests.map(request -> toResponse(request, itemsByRequestId))
        );
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<MedicineRequestResponse> getAllRequests(String lang, Pageable pageable) {
        String language = normalizeLanguage(lang);
        User currentUser = currentUserProvider.get();
        if (!isApplicationAdmin(currentUser)) {
            throw new AccessDeniedException("Only application administrators can view all medicine requests");
        }

        Page<MedicineRequest> requests = medicineRequestRepository.findAll(pageable);
        Map<Long, List<MedicineRequestItemResponse>> itemsByRequestId = resolveItems(requests.getContent(), language);
        return PaginatedResponse.from(
                requests.map(request -> toResponse(request, itemsByRequestId))
        );
    }

    @Transactional
    public MedicineRequestResultResponse getMedicineRequestResult(Long medicineRequestId, String lang){
        String language = normalizeLanguage(lang);
        MedicineRequest medicineRequest = medicineRequestRepository.findDetailedById(medicineRequestId).orElseThrow(()->new ResourceNotFoundException("Medicine Request not found"));
        if (!medicineRequest.getCustomer().getId().equals(currentUserProvider.get().getId())) {
            throw new AccessDeniedException("You are not allowed to view this medicine request result");
        }
        if (medicineRequest.getStatus() == RequestStatus.EXPIRED) {
            throw new IllegalArgumentException("You can't view this Request's Result, the Request is EXPIRED");
        }
        if(medicineRequest.getStatus() == RequestStatus.COMPLETED){
            throw new IllegalArgumentException("You can't view this Request's Result, the Request is already COMPLETED");
        }
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

        List<Long> productIds = new ArrayList<>();
        for(RequestItem requestItem : requestItems){
           PharmacyOfferItem bestOffer = bestOfferItems.get(requestItem.getId());
            MedicineRequestResultItemResponse medicineRequestResultItemResponse;
            if(bestOffer == null){
                medicineRequestResultItemResponse = MedicineRequestResultMapper.unavailable(requestItem.getId());
            }
            else{
               medicineRequestResultItemResponse = medicineRequestResultItemMapper.toResponse(bestOffer);
               totalPrice = totalPrice.add(bestOffer.getProduct().getPrice().multiply(BigDecimal.valueOf(requestItem.getQuantity())));
               if (bestOffer.getProduct() != null) {
                   productIds.add(bestOffer.getProduct().getId());
               }
            }
             medicineRequestResultItemResponseList.add(medicineRequestResultItemResponse);
        }

        if (!productIds.isEmpty()) {
            Map<Long, ProductSummaryResponse> productsById = productRepository
                    .findAllLocalized(productIds, language, DEFAULT_LANG)
                    .stream()
                    .collect(Collectors.toMap(ProductSummaryResponse::id, Function.identity()));
            medicineRequestResultItemResponseList.forEach(item -> {
                if (item.getProductId() != null) {
                    item.setProduct(productsById.get(item.getProductId()));
                }
            });
        }

        return new MedicineRequestResultResponse(medicineRequestResultItemResponseList, totalPrice, medicineRequest.getPaymentMethod());
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<MedicineRequestResponse> getPharmacyRequests(Long pharmacyId, String lang, Pageable pageable) {
        String language = normalizeLanguage(lang);
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy not found"));
        requireCurrentPharmacistForPharmacy(pharmacy);

        Page<MedicineRequest> requests =
                medicineRequestRepository.findDistinctByOffers_Pharmacy_Id(pharmacyId, pageable);
        Map<Long, List<MedicineRequestItemResponse>> itemsByRequestId = resolveItems(requests.getContent(), language);
        return PaginatedResponse.from(
                requests.map(request -> toResponse(request, itemsByRequestId))
        );
    }

    @Transactional(readOnly = true)
    public MedicineRequestResponse getRequestById(Long id, String lang) {
        String language = normalizeLanguage(lang);
        MedicineRequest medicineRequest = findRequest(id);
        User currentUser = currentUserProvider.get();

        boolean ownsRequest = currentUser instanceof Customer
                && medicineRequest.getCustomer().getId().equals(currentUser.getId());
        boolean pharmacyReceivedRequest = currentUser instanceof Pharmacist pharmacist
                && pharmacist.getPharmacy() != null
                && pharmacyAssignmentRepository.existsByMedicineRequest_IdAndPharmacy_Id(
                        medicineRequest.getId(),
                        pharmacist.getPharmacy().getId()
                );
        if (!isApplicationAdmin(currentUser) && !ownsRequest && !pharmacyReceivedRequest) {
            throw new AccessDeniedException("You are not allowed to view this medicine request");
        }

        return toResponse(medicineRequest, resolveItems(List.of(medicineRequest), language));
    }

    private MedicineRequestResponse toResponse(
            MedicineRequest request,
            Map<Long, List<MedicineRequestItemResponse>> itemsByRequestId
    ) {
        return medicineRequestMapper.toResponse(request, itemsByRequestId.getOrDefault(request.getId(), List.of()));
    }

    /**
     * Raw line data is fetched for all requests in one query (product left
     * null), then the distinct products are resolved in a single localized
     * query and filled in place. Results are grouped by request id.
     */
    private Map<Long, List<MedicineRequestItemResponse>> resolveItems(List<MedicineRequest> requests, String lang) {
        List<Long> requestIds = requests.stream()
                .map(MedicineRequest::getId)
                .toList();
        if (requestIds.isEmpty()) {
            return Map.of();
        }

        List<MedicineRequestItemResponse> items = requestItemRepository.findByRequestIdIn(requestIds);

        List<Long> productIds = items.stream()
                .map(MedicineRequestItemResponse::getProductId)
                .filter(id -> id != null)   // deleted products have null productId
                .distinct()
                .toList();

        if (!productIds.isEmpty()) {
            Map<Long, ProductSummaryResponse> productsById = productRepository
                    .findAllLocalized(productIds, lang, DEFAULT_LANG)
                    .stream()
                    .collect(Collectors.toMap(ProductSummaryResponse::id, Function.identity()));

            items.forEach(item -> {
                ProductSummaryResponse product = productsById.get(item.getProductId());
                item.setProduct(product);
                if (product != null && product.price() != null) {
                    item.setUnitPrice(product.price().doubleValue());
                }
            });
        }

        return items.stream().collect(Collectors.groupingBy(
                MedicineRequestItemResponse::getRequestId,
                LinkedHashMap::new,
                Collectors.toList()
        ));
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

    private String normalizeLanguage(String lang) {
        String language = StringUtils.hasText(lang)
                ? lang.trim().toLowerCase(Locale.ROOT)
                : DEFAULT_LANG;
        if (!DEFAULT_LANG.equals(language) && !ARABIC.equals(language)) {
            throw new IllegalArgumentException("Unsupported language. Supported values are en and ar");
        }
        return language;
    }

}
