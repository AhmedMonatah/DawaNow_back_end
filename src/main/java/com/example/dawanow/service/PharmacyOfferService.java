package com.example.dawanow.service;

import com.example.dawanow.dtos.request.CreateOfferItemRequest;
import com.example.dawanow.dtos.request.CreateOfferRequest;
import com.example.dawanow.dtos.response.*;
import com.example.dawanow.entity.*;
import com.example.dawanow.exception.ResourceNotFoundException;
import com.example.dawanow.mapper.PharmacyOfferMapper;
import com.example.dawanow.repo.PharmacyAssignmentRepository;
import com.example.dawanow.repo.PharmacyOfferItemRepository;
import com.example.dawanow.repo.PharmacyOfferRepository;
import com.example.dawanow.repo.ProductRepository;
import com.example.dawanow.repo.RequestItemRepository;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.file.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PharmacyOfferService {

    private static final String DEFAULT_LANG = "en";
    private static final String ARABIC = "ar";

    private final MedicineRequestService medicineRequestService;
    private final CurrentUserProvider currentUserProvider;
    private final PharmacyAssignmentRepository pharmacyAssignmentRepository;
    private final ProductRepository productRepository;
    private final RequestItemRepository requestItemRepository;
    private final PharmacyOfferRepository pharmacyOfferRepository;
    private final PharmacyOfferItemRepository pharmacyOfferItemRepository;
    private final PharmacyOfferMapper pharmacyOfferMapper;

    public PaginatedResponse<PharmacyOfferResponse> getOffersByPharmacy(Long pharmacyId, String lang, Pageable pageable) {
        normalizeLanguage(lang);
        return PaginatedResponse.empty(pageable);
    }

    @Transactional
    public PharmacyOfferResponse createOffer(
            Long requestId,
            CreateOfferRequest request,
            String lang
    ) throws AccessDeniedException, BadRequestException {
        String language = normalizeLanguage(lang);
        Pharmacist pharmacist = (Pharmacist) currentUserProvider.get();


        if(pharmacyOfferRepository.existsByPharmacyIdAndRequestId(
                pharmacist.getPharmacy().getId(), requestId)){
            throw new BadRequestException("You have already made an offer for this request");
        }

        MedicineRequest medicineRequest = medicineRequestService.getEntity(requestId);



        PharmacyAssignment assignment = pharmacyAssignmentRepository
                .findByPharmacyIdAndMedicineRequestId(pharmacist.getPharmacy().getId(), requestId)
                .orElseThrow(() -> new AccessDeniedException("This request was not assigned to your pharmacy"));

        PharmacyOffer offer = new PharmacyOffer();
        offer.setRequest(medicineRequest);
        offer.setPharmacy(pharmacist.getPharmacy());
        offer.setPharmacist(pharmacist);
        offer.setDistanceKm(assignment.getDistanceKm());

        if (medicineRequest.getStatus() == RequestStatus.SEARCHING) {
            medicineRequest.setStatus(RequestStatus.OFFERS_READY);
        }


        BigDecimal totalOfferPrice = BigDecimal.ZERO;
        for (CreateOfferItemRequest itemDto : request.items()) {
            PharmacyOfferItem pharmacyOfferItem = new PharmacyOfferItem();
            pharmacyOfferItem.setOffer(offer);

            Product product = productRepository.findById(itemDto.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

            RequestItem requestItem = requestItemRepository.findById(itemDto.requestItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Request item not found"));

            if (!requestItem.getRequest().getId().equals(requestId)) {
                throw new BadRequestException("Request item does not belong to this request");
            }
            pharmacyOfferItem.setRequestItem(requestItem);

            Long requestedProductId = requestItem.getProduct().getId();

            pharmacyOfferItem.setProduct(product);
            if(!pharmacyOfferItem.getProduct().getId().equals(requestedProductId)){
                pharmacyOfferItem.setAlternative(true);
            }
            offer.getItems().add(pharmacyOfferItem);
        }
        //offer.setTotalPrice(totalOfferPrice);
        pharmacyOfferRepository.save(offer);
        return toResponse(offer, resolveItems(List.of(offer), language));
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<PharmacyOfferResponse> getRequestOffers(Long requestId, String lang, Pageable pageable){
        String language = normalizeLanguage(lang);

        Page<PharmacyOffer> offers =
                pharmacyOfferRepository.findByRequestIdOrderByDistanceKmAsc(requestId, pageable);

        Map<Long, List<PharmacyOfferItemResponse>> itemsByOfferId =
                resolveItems(offers.getContent(), language);

        return PaginatedResponse.from(
                offers.map(offer -> toResponse(offer, itemsByOfferId))
        );
    }

    @Transactional(readOnly = true)
    public PharmacyOfferResponse getOfferById(Long id, String lang) {
        String language = normalizeLanguage(lang);
        PharmacyOffer pharmacyOffer = pharmacyOfferRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Offer Not Found"));
        return toResponse(pharmacyOffer, resolveItems(List.of(pharmacyOffer), language));
    }

    private PharmacyOfferResponse toResponse(PharmacyOffer offer, Map<Long, List<PharmacyOfferItemResponse>> itemsByOfferId) {
        return pharmacyOfferMapper.toResponse(offer, itemsByOfferId.getOrDefault(offer.getId(), List.of()));
    }

    /**
     * Raw line data is fetched for all offers in one query (product left null),
     * then the distinct products are resolved in a single localized query and
     * filled in place. Results are grouped by offer id.
     */
    private Map<Long, List<PharmacyOfferItemResponse>> resolveItems(List<PharmacyOffer> offers, String lang) {
        List<Long> offerIds = offers.stream()
                .map(PharmacyOffer::getId)
                .toList();
        if (offerIds.isEmpty()) {
            return Map.of();
        }

        List<PharmacyOfferItemResponse> items = pharmacyOfferItemRepository.findByOfferIdIn(offerIds);

        List<Long> productIds = items.stream()
                .map(PharmacyOfferItemResponse::getProductId)
                .filter(id -> id != null)   // deleted products have null productId
                .distinct()
                .toList();

        if (!productIds.isEmpty()) {
            Map<Long, ProductSummaryResponse> productsById = productRepository
                    .findAllLocalized(productIds, lang, DEFAULT_LANG)
                    .stream()
                    .collect(Collectors.toMap(ProductSummaryResponse::id, Function.identity()));

            items.forEach(item -> item.setProduct(productsById.get(item.getProductId())));
        }

        return items.stream().collect(Collectors.groupingBy(
                PharmacyOfferItemResponse::getOfferId,
                LinkedHashMap::new,
                Collectors.toList()
        ));
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
