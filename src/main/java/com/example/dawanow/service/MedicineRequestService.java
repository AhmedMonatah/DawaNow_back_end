package com.example.dawanow.service;

import com.example.dawanow.dtos.request.CreateMedicineRequestRequest;
import com.example.dawanow.dtos.response.MedicineRequestItemResponse;
import com.example.dawanow.dtos.response.MedicineRequestResponse;
import com.example.dawanow.dtos.response.PharmacyMedicineRequestResponse;
import com.example.dawanow.dtos.response.MedicineRequestResultItemResponse;
import com.example.dawanow.dtos.response.MedicineRequestResultResponse;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.dtos.response.ProductSummaryResponse;
import com.example.dawanow.dtos.response.RequestItemStatusUpdate;
import com.example.dawanow.dtos.response.RequestResultUpdateEvent;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
import java.util.Objects;
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
    private final RequestResultSseService requestResultSseService;

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

        medicineRequest.setStatus(RequestStatus.SEARCHING);
        medicineRequestRepository.save(medicineRequest);

        assignmentService.assignNearbyPharmacies(medicineRequest);

        cartService.clearCart();

        return toResponse(medicineRequest, resolveItems(List.of(medicineRequest), language));
    }

    public MedicineRequest getEntity(Long medicineRequestId){
        return medicineRequestRepository.findById(medicineRequestId).orElseThrow(()->new ResourceNotFoundException("Medicine Request not found"));
    }

    @Transactional
    public PaginatedResponse<PharmacyMedicineRequestResponse> getCurrentPharmacyRequests(String lang,
                                                                                          AssignmentStatus status,
                                                                                          Pageable pageable) {
        String language = normalizeLanguage(lang);

        Pharmacist pharmacist = (Pharmacist) currentUserProvider.get();

        if (pharmacist.getPharmacy() == null) {
            throw new ResourceNotFoundException("Current pharmacist is not assigned to any pharmacy");
        }

        Long pharmacyId = pharmacist.getPharmacy().getId();

        Page<PharmacyAssignment> assignments = (status == null)
                ? pharmacyAssignmentRepository.getPharmacyAssignmentsByPharmacy_Id(pharmacyId, pageable)
                : pharmacyAssignmentRepository.getPharmacyAssignmentsByPharmacy_IdAndStatus(pharmacyId, status, pageable);

        List<MedicineRequest> requests = assignments.getContent().stream()
                .map(PharmacyAssignment::getMedicineRequest)
                .toList();
        Map<Long, List<MedicineRequestItemResponse>> itemsByRequestId = resolveItems(requests, language);

        return PaginatedResponse.from(
                assignments.map(assignment -> new PharmacyMedicineRequestResponse(
                        toResponse(assignment.getMedicineRequest(), itemsByRequestId),
                        assignment.getStatus(),
                        assignment.getDistanceKm()
                ))
        );
    }

    @Transactional
    @Scheduled(fixedRate = 60000)
    public void expireRequests() {
        List<MedicineRequest> medicineRequestList =  medicineRequestRepository.findByStatusAndExpiresAtBefore(RequestStatus.SEARCHING, LocalDateTime.now());
        for (MedicineRequest medicineRequest : medicineRequestList) {
            medicineRequest.setStatus(RequestStatus.EXPIRED);
        }
        List<Long> requestIds = medicineRequestList.stream()
                .map(MedicineRequest::getId)
                .toList();
        expirePendingAssignments(requestIds);
    }

    /**
     * Expires the still-pending pharmacy assignments for the given requests.
     * Assignments with other statuses (e.g. OFFER_CREATED) are left untouched.
     */
    @Transactional
    public void expirePendingAssignments(List<Long> requestIds) {
        if (requestIds.isEmpty()) {
            return;
        }
        List<PharmacyAssignment> assignments =
                pharmacyAssignmentRepository.findByMedicineRequest_IdInAndStatus(requestIds, AssignmentStatus.PENDING);
        for (PharmacyAssignment assignment : assignments) {
            assignment.setStatus(AssignmentStatus.EXPIRED);
        }
    }

    @Transactional(readOnly = true)
    public PharmacyMedicineRequestResponse getCurrentPharmacyRequest(Long requestId, String lang) {
        String language = normalizeLanguage(lang);

        Pharmacist pharmacist = (Pharmacist) currentUserProvider.get();

        if (pharmacist.getPharmacy() == null) {
            throw new ResourceNotFoundException("Current pharmacist is not assigned to any pharmacy");
        }

        PharmacyAssignment assignment = pharmacyAssignmentRepository
                .findByPharmacyIdAndMedicineRequestId(pharmacist.getPharmacy().getId(), requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine request not found"));

        MedicineRequest request = assignment.getMedicineRequest();
        Map<Long, List<MedicineRequestItemResponse>> itemsByRequestId = resolveItems(List.of(request), language);

        return new PharmacyMedicineRequestResponse(
                toResponse(request, itemsByRequestId),
                assignment.getStatus(),
                assignment.getDistanceKm()
        );
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
        requireCustomerOwnsRequest(medicineRequest);
        if (medicineRequest.getStatus() == RequestStatus.EXPIRED) {
            throw new IllegalArgumentException("You can't view this Request's Result, the Request is EXPIRED");
        }
        if(medicineRequest.getStatus() == RequestStatus.COMPLETED){
            throw new IllegalArgumentException("You can't view this Request's Result, the Request is already COMPLETED");
        }
        return computeResult(medicineRequest, language);
    }

    /**
     * Updates request item statuses from the items of a single newly created
     * offer, using the existing status already stored on each RequestItem.
     * Exact offers upgrade status to FOUND. Alternative offers are only emitted
     * when they introduce a genuinely NEW alternative (no prior alternative
     * offer item for the same request item/product, excluding the current
     * offer); repeats are silently ignored and never regress a status. The SSE
     * delta carries the newly found alternative product. Runs inside the
     * offer-creation transaction and takes the pessimistic write lock on the
     * request row, so concurrent offers for the same request are serialized
     * and an older alternative offer can never regress a FOUND status.
     */
    @Transactional
    public void updateRequestItemStatuses(Long requestId, PharmacyOffer offer, String lang) {
        String language = normalizeLanguage(lang);
        medicineRequestRepository.findDetailedById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine Request not found"));

        List<RequestItemStatusUpdate> pending = new ArrayList<>();
        Map<Long, Long> alternativeProductIdByRequestItemId = new HashMap<>();
        collectStatusUpdates(offer, pending, alternativeProductIdByRequestItemId);

        if (pending.isEmpty()) {
            return;
        }

        Map<Long, ProductSummaryResponse> productsById = resolveAlternativeProducts(
                alternativeProductIdByRequestItemId, language);

        List<RequestItemStatusUpdate> updatedItems = pending.stream()
                .map(update -> update.status() == RequestItemStatus.ALTERNATIVE_FOUND
                        ? new RequestItemStatusUpdate(
                                update.requestItemId(),
                                update.status(),
                                productsById.get(alternativeProductIdByRequestItemId.get(update.requestItemId())))
                        : update)
                .toList();

        publishAfterCommit(requestId, new RequestResultUpdateEvent(requestId, updatedItems));
    }

    private void collectStatusUpdates(
            PharmacyOffer offer,
            List<RequestItemStatusUpdate> pending,
            Map<Long, Long> alternativeProductIdByRequestItemId
    ) {
        for (PharmacyOfferItem item : offer.getItems()) {
            RequestItem requestItem = item.getRequestItem();
            Long requestItemId = requestItem.getId();

            if (item.isAlternative()) {
                if (requestItem.getStatus() == RequestItemStatus.FOUND || item.getProduct() == null) {
                    continue;
                }
                boolean newAlternative = !existsPriorAlternative(requestItemId, item.getProduct().getId(), offer.getId());
                if (!newAlternative) {
                    continue;
                }
                if (requestItem.getStatus() == RequestItemStatus.NOT_FOUND) {
                    requestItem.setStatus(RequestItemStatus.ALTERNATIVE_FOUND);
                }
                alternativeProductIdByRequestItemId.put(requestItemId, item.getProduct().getId());
                pending.add(new RequestItemStatusUpdate(requestItemId, RequestItemStatus.ALTERNATIVE_FOUND, null));
            } else if (requestItem.getStatus() != RequestItemStatus.FOUND) {
                requestItem.setStatus(RequestItemStatus.FOUND);
                pending.add(new RequestItemStatusUpdate(requestItemId, RequestItemStatus.FOUND, null));
            }
        }
    }

    private boolean existsPriorAlternative(Long requestItemId, Long productId, Long excludedOfferId) {
        return pharmacyOfferItemRepository.existsPriorAlternative(requestItemId, productId, excludedOfferId);
    }

    private Map<Long, ProductSummaryResponse> resolveAlternativeProducts(
            Map<Long, Long> productIdByRequestItemId, String language) {
        if (productIdByRequestItemId.isEmpty()) {
            return Map.of();
        }
        List<Long> productIds = productIdByRequestItemId.values().stream().distinct().toList();
        return productRepository.findAllLocalized(productIds, language, DEFAULT_LANG)
                .stream()
                .collect(Collectors.toMap(ProductSummaryResponse::id, Function.identity()));
    }

    private MedicineRequestResultResponse computeResult(MedicineRequest medicineRequest, String language) {
        List<MedicineRequestResultItemResponse> medicineRequestResultItemResponseList = new ArrayList<>();

        BigDecimal totalPrice = BigDecimal.ZERO;

        List<RequestItem> requestItems = medicineRequest.getItems();
        Map<Long, PharmacyOfferItem> bestOfferItems = bestOfferItems(medicineRequest);

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

        populateAlternatives(medicineRequestResultItemResponseList, bestOfferItems, language);

        return new MedicineRequestResultResponse(medicineRequestResultItemResponseList, totalPrice, medicineRequest.getPaymentMethod());
    }

    private void populateAlternatives(
            List<MedicineRequestResultItemResponse> responses,
            Map<Long, PharmacyOfferItem> bestOfferItems,
            String language
    ) {
        List<Long> alternativeRequestItemIds = bestOfferItems.values().stream()
                .filter(PharmacyOfferItem::isAlternative)
                .map(item -> item.getRequestItem().getId())
                .distinct()
                .toList();

        if (alternativeRequestItemIds.isEmpty()) {
            return;
        }

        Map<Long, List<Long>> productIdsByRequestItemId = pharmacyOfferItemRepository
                .findAlternativeProductIdsByRequestItemIdIn(alternativeRequestItemIds)
                .stream()
                .collect(Collectors.groupingBy(
                        row -> (Long) row[0],
                        Collectors.mapping(row -> (Long) row[1], Collectors.toList())));

        Map<Long, ProductSummaryResponse> productsById = productRepository
                .findAllLocalized(productIdsByRequestItemId.values().stream().flatMap(List::stream).distinct().toList(), language, DEFAULT_LANG)
                .stream()
                .collect(Collectors.toMap(ProductSummaryResponse::id, Function.identity()));

        responses.forEach(item -> {
            List<ProductSummaryResponse> alternatives = productIdsByRequestItemId
                    .getOrDefault(item.getRequestItemId(), List.of())
                    .stream()
                    .map(productsById::get)
                    .filter(Objects::nonNull)
                    .toList();
            item.setAlternatives(alternatives);
        });
    }

    private Map<Long, PharmacyOfferItem> bestOfferItems(MedicineRequest medicineRequest) {
        Map<Long, PharmacyOfferItem> bestOfferItems = new HashMap<>();

        for (PharmacyOffer offer : medicineRequest.getOffers()) {
            for (PharmacyOfferItem item : offer.getItems()) {

                PharmacyOfferItem current =
                        bestOfferItems.get(item.getRequestItem().getId());

                if (current == null
                        || (current.isAlternative() && !item.isAlternative())) {

                    bestOfferItems.put(item.getRequestItem().getId(), item);
                }
            }
        }
        return bestOfferItems;
    }

    private void publishAfterCommit(Long requestId, RequestResultUpdateEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            requestResultSseService.publishDelta(requestId, event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                requestResultSseService.publishDelta(requestId, event);
            }
        });
    }

    private void requireCustomerOwnsRequest(MedicineRequest medicineRequest) {
        if (!medicineRequest.getCustomer().getId().equals(currentUserProvider.get().getId())) {
            throw new AccessDeniedException("You are not allowed to view this medicine request result");
        }
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
        if (!isApplicationAdmin(currentUser) && !ownsRequest) {
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
