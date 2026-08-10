package com.example.dawanow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.dawanow.dtos.response.MedicineRequestItemResponse;
import com.example.dawanow.dtos.response.MedicineRequestResponse;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.dtos.response.PharmacyMedicineRequestResponse;
import com.example.dawanow.dtos.response.ProductSummaryResponse;
import com.example.dawanow.dtos.response.RequestItemStatusUpdate;
import com.example.dawanow.dtos.response.RequestResultUpdateEvent;
import com.example.dawanow.entity.AssignmentStatus;
import com.example.dawanow.entity.Customer;
import com.example.dawanow.entity.MedicineRequest;
import com.example.dawanow.entity.Pharmacist;
import com.example.dawanow.entity.Pharmacy;
import com.example.dawanow.entity.PharmacyAssignment;
import com.example.dawanow.entity.PharmacyOffer;
import com.example.dawanow.entity.PharmacyOfferItem;
import com.example.dawanow.entity.Product;
import com.example.dawanow.entity.RequestItem;
import com.example.dawanow.entity.RequestItemStatus;
import com.example.dawanow.entity.RequestStatus;
import com.example.dawanow.exception.ResourceNotFoundException;
import com.example.dawanow.mapper.MedicineRequestMapper;
import com.example.dawanow.mapper.MedicineRequestResultItemMapper;
import com.example.dawanow.repo.MedicineRequestRepository;
import com.example.dawanow.repo.PharmacyAssignmentRepository;
import com.example.dawanow.repo.PharmacyOfferItemRepository;
import com.example.dawanow.repo.PharmacyRepository;
import com.example.dawanow.repo.ProductRepository;
import com.example.dawanow.repo.RequestItemRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class MedicineRequestServiceTest {

    @Mock
    private MedicineRequestRepository medicineRequestRepository;
    @Mock
    private PharmacyRepository pharmacyRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private MedicineRequestMapper medicineRequestMapper;
    @Mock
    private CartService cartService;
    @Mock
    private AssignmentService assignmentService;
    @Mock
    private PharmacyAssignmentRepository pharmacyAssignmentRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private ProductService productService;
    @Mock
    private PharmacyOfferItemRepository pharmacyOfferItemRepository;
    @Mock
    private MedicineRequestResultItemMapper medicineRequestResultItemMapper;
    @Mock
    private RequestItemRepository requestItemRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private RequestResultSseService requestResultSseService;

    private MedicineRequestService service;

    @BeforeEach
    void setUp() {
        service = new MedicineRequestService(
                medicineRequestRepository, pharmacyRepository, currentUserProvider,
                medicineRequestMapper, cartService, assignmentService,
                pharmacyAssignmentRepository, fileStorageService, productService,
                pharmacyOfferItemRepository, medicineRequestResultItemMapper,
                requestItemRepository, productRepository, requestResultSseService
        );
    }

    @Test
    void getCurrentPharmacyRequestsReturnsAssignmentStatusAndDistance() {
        Pharmacy pharmacy = new Pharmacy();
        pharmacy.setId(5L);
        Pharmacist pharmacist = new Pharmacist();
        pharmacist.setPharmacy(pharmacy);

        Customer customer = new Customer();
        customer.setId(3L);

        MedicineRequest request = new MedicineRequest();
        request.setId(8L);
        request.setCustomer(customer);

        PharmacyAssignment assignment = new PharmacyAssignment();
        assignment.setMedicineRequest(request);
        assignment.setStatus(AssignmentStatus.OFFER_CREATED);
        assignment.setDistanceKm(3.2);

        Pageable pageable = PageRequest.of(0, 10);
        when(currentUserProvider.get()).thenReturn(pharmacist);
        when(pharmacyAssignmentRepository.getPharmacyAssignmentsByPharmacy_Id(5L, pageable))
                .thenReturn(new PageImpl<>(List.of(assignment), pageable, 1));
        when(requestItemRepository.findByRequestIdIn(List.of(8L))).thenReturn(List.of());

        MedicineRequestResponse nested = new MedicineRequestResponse(
                8L, 3L, "name", "phone", null, null, null, null, null, List.of(), null, null, null);
        when(medicineRequestMapper.toResponse(request, List.of())).thenReturn(nested);

        PaginatedResponse<PharmacyMedicineRequestResponse> result =
                service.getCurrentPharmacyRequests("en", null, pageable);

        assertThat(result.content()).hasSize(1);
        PharmacyMedicineRequestResponse item = result.content().getFirst();
        assertThat(item.assignmentStatus()).isEqualTo(AssignmentStatus.OFFER_CREATED);
        assertThat(item.distanceKm()).isEqualTo(3.2);
        assertThat(item.request()).isSameAs(nested);
    }

    @Test
    void getCurrentPharmacyRequestsFiltersByStatus() {
        Pharmacy pharmacy = new Pharmacy();
        pharmacy.setId(5L);
        Pharmacist pharmacist = new Pharmacist();
        pharmacist.setPharmacy(pharmacy);

        Customer customer = new Customer();
        customer.setId(3L);

        MedicineRequest request = new MedicineRequest();
        request.setId(8L);
        request.setCustomer(customer);

        PharmacyAssignment assignment = new PharmacyAssignment();
        assignment.setMedicineRequest(request);
        assignment.setStatus(AssignmentStatus.PENDING);
        assignment.setDistanceKm(1.5);

        Pageable pageable = PageRequest.of(0, 10);
        when(currentUserProvider.get()).thenReturn(pharmacist);
        when(pharmacyAssignmentRepository.getPharmacyAssignmentsByPharmacy_IdAndStatus(5L, AssignmentStatus.PENDING, pageable))
                .thenReturn(new PageImpl<>(List.of(assignment), pageable, 1));
        when(requestItemRepository.findByRequestIdIn(List.of(8L))).thenReturn(List.of());

        MedicineRequestResponse nested = new MedicineRequestResponse(
                8L, 3L, "name", "phone", null, null, null, null, null, List.of(), null, null, null);
        when(medicineRequestMapper.toResponse(request, List.of())).thenReturn(nested);

        PaginatedResponse<PharmacyMedicineRequestResponse> result =
                service.getCurrentPharmacyRequests("en", AssignmentStatus.PENDING, pageable);

        assertThat(result.content()).hasSize(1);
        PharmacyMedicineRequestResponse item = result.content().getFirst();
        assertThat(item.assignmentStatus()).isEqualTo(AssignmentStatus.PENDING);
        assertThat(item.distanceKm()).isEqualTo(1.5);
    }

    @Test
    void getCurrentPharmacyRequestReturnsAssignmentAndDistance() {
        Pharmacy pharmacy = new Pharmacy();
        pharmacy.setId(5L);
        Pharmacist pharmacist = new Pharmacist();
        pharmacist.setPharmacy(pharmacy);

        Customer customer = new Customer();
        customer.setId(3L);

        MedicineRequest request = new MedicineRequest();
        request.setId(8L);
        request.setCustomer(customer);

        PharmacyAssignment assignment = new PharmacyAssignment();
        assignment.setMedicineRequest(request);
        assignment.setStatus(AssignmentStatus.OFFER_CREATED);
        assignment.setDistanceKm(3.2);

        when(currentUserProvider.get()).thenReturn(pharmacist);
        when(pharmacyAssignmentRepository.findByPharmacyIdAndMedicineRequestId(5L, 8L))
                .thenReturn(Optional.of(assignment));
        when(requestItemRepository.findByRequestIdIn(List.of(8L))).thenReturn(List.of());

        MedicineRequestResponse nested = new MedicineRequestResponse(
                8L, 3L, "name", "phone", null, null, null, null, null, List.of(), null, null, null);
        when(medicineRequestMapper.toResponse(request, List.of())).thenReturn(nested);

        PharmacyMedicineRequestResponse result = service.getCurrentPharmacyRequest(8L, "en");

        assertThat(result.assignmentStatus()).isEqualTo(AssignmentStatus.OFFER_CREATED);
        assertThat(result.distanceKm()).isEqualTo(3.2);
        assertThat(result.request()).isSameAs(nested);
    }

    @Test
    void getCurrentPharmacyRequestThrowsWhenNotAssigned() {
        Pharmacy pharmacy = new Pharmacy();
        pharmacy.setId(5L);
        Pharmacist pharmacist = new Pharmacist();
        pharmacist.setPharmacy(pharmacy);

        when(currentUserProvider.get()).thenReturn(pharmacist);
        when(pharmacyAssignmentRepository.findByPharmacyIdAndMedicineRequestId(5L, 8L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrentPharmacyRequest(8L, "en"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getRequestByIdDeniesPharmacist() {
        MedicineRequest request = new MedicineRequest();
        request.setId(8L);
        request.setCustomer(new Customer());

        Pharmacist pharmacist = new Pharmacist();
        pharmacist.setPharmacy(new Pharmacy());

        when(medicineRequestRepository.findById(8L)).thenReturn(Optional.of(request));
        when(currentUserProvider.get()).thenReturn(pharmacist);

        assertThatThrownBy(() -> service.getRequestById(8L, "en"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void expireRequestsMarksAssignmentsExpired() {
        MedicineRequest request = new MedicineRequest();
        request.setId(8L);

        PharmacyAssignment assignment = new PharmacyAssignment();
        assignment.setStatus(AssignmentStatus.PENDING);

        when(medicineRequestRepository.findByStatusAndExpiresAtBefore(
                eq(RequestStatus.PENDING), any(LocalDateTime.class))).thenReturn(List.of(request));
        when(pharmacyAssignmentRepository.findByMedicineRequest_IdIn(List.of(8L)))
                .thenReturn(List.of(assignment));

        service.expireRequests();

        assertThat(request.getStatus()).isEqualTo(RequestStatus.EXPIRED);
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.EXPIRED);
    }

    @Test
    void resolvesLocalizedProductsForRequestItems() {
        Customer customer = new Customer();
        customer.setId(3L);
        MedicineRequest request = new MedicineRequest();
        request.setId(8L);
        request.setCustomer(customer);

        when(currentUserProvider.get()).thenReturn(customer);
        when(medicineRequestRepository.findById(8L)).thenReturn(Optional.of(request));

        MedicineRequestItemResponse item = new MedicineRequestItemResponse(1L, 8L, 11L, 2L);
        when(requestItemRepository.findByRequestIdIn(List.of(8L))).thenReturn(List.of(item));

        ProductSummaryResponse summary = new ProductSummaryResponse(
                11L, "بانادول", "بانادول", null, null, null, new BigDecimal("10.00"),
                "باراسيتامول", null, null, null, "img.png");
        when(productRepository.findAllLocalized(List.of(11L), "ar", "en")).thenReturn(List.of(summary));

        MedicineRequestResponse expected = new MedicineRequestResponse(
                8L, 3L, "name", "phone", null, null, null, null, null, List.of(item), null, null, null);
        when(medicineRequestMapper.toResponse(request, List.of(item))).thenReturn(expected);

        MedicineRequestResponse result = service.getRequestById(8L, "ar");

        assertThat(result).isEqualTo(expected);
        assertThat(item.getProduct()).isSameAs(summary);
        assertThat(item.getUnitPrice()).isEqualTo(10.0);
    }

    @Test
    void updatesNotFoundToFoundAndPublishesDelta() {
        RequestItemStatus upgraded = runUpdateWith(RequestItemStatus.NOT_FOUND, false, false);

        assertThat(upgraded).isEqualTo(RequestItemStatus.FOUND);

        ArgumentCaptor<RequestResultUpdateEvent> captor = ArgumentCaptor.forClass(RequestResultUpdateEvent.class);
        verify(requestResultSseService).publishDelta(eq(8L), captor.capture());
        RequestResultUpdateEvent event = captor.getValue();
        assertThat(event.requestId()).isEqualTo(8L);
        assertThat(event.updatedItems()).hasSize(1);
        assertThat(event.updatedItems().getFirst())
                .isEqualTo(new RequestItemStatusUpdate(1L, RequestItemStatus.FOUND, null));
    }

    @Test
    void updatesNotFoundToAlternativeFoundWithAlternativeProduct() {
        RequestItemStatus upgraded = runUpdateWith(RequestItemStatus.NOT_FOUND, true, false);

        assertThat(upgraded).isEqualTo(RequestItemStatus.ALTERNATIVE_FOUND);

        ArgumentCaptor<RequestResultUpdateEvent> captor = ArgumentCaptor.forClass(RequestResultUpdateEvent.class);
        verify(requestResultSseService).publishDelta(eq(8L), captor.capture());
        RequestItemStatusUpdate update = captor.getValue().updatedItems().getFirst();
        assertThat(update.status()).isEqualTo(RequestItemStatus.ALTERNATIVE_FOUND);
        assertThat(update.product()).isNotNull();
        assertThat(update.product().id()).isEqualTo(11L);
    }

    @Test
    void updatesAlternativeFoundToFound() {
        RequestItemStatus upgraded = runUpdateWith(RequestItemStatus.ALTERNATIVE_FOUND, false, false);

        assertThat(upgraded).isEqualTo(RequestItemStatus.FOUND);
    }

    @Test
    void alternativeOfferDoesNotDowngradeFound() {
        RequestItemStatus upgraded = runUpdateWith(RequestItemStatus.FOUND, true, false);

        assertThat(upgraded).isEqualTo(RequestItemStatus.FOUND);
        verifyNoInteractions(requestResultSseService);
    }

    @Test
    void repeatAlternativeLeavesAlternativeFoundUnchanged() {
        RequestItemStatus upgraded = runUpdateWith(RequestItemStatus.ALTERNATIVE_FOUND, true, true);

        assertThat(upgraded).isEqualTo(RequestItemStatus.ALTERNATIVE_FOUND);
        verifyNoInteractions(requestResultSseService);
    }

    @Test
    void newAlternativeWhileAlternativeFoundPublishesWithoutRegressingStatus() {
        RequestItemStatus upgraded = runUpdateWith(RequestItemStatus.ALTERNATIVE_FOUND, true, false);

        assertThat(upgraded).isEqualTo(RequestItemStatus.ALTERNATIVE_FOUND);

        ArgumentCaptor<RequestResultUpdateEvent> captor = ArgumentCaptor.forClass(RequestResultUpdateEvent.class);
        verify(requestResultSseService).publishDelta(eq(8L), captor.capture());
        RequestItemStatusUpdate update = captor.getValue().updatedItems().getFirst();
        assertThat(update.status()).isEqualTo(RequestItemStatus.ALTERNATIVE_FOUND);
        assertThat(update.product().id()).isEqualTo(11L);
    }

    private RequestItemStatus runUpdateWith(
            RequestItemStatus initialStatus,
            boolean alternativeOffer,
            boolean priorAlternativeExists
    ) {
        Customer customer = new Customer();
        customer.setId(3L);

        Product product = new Product();
        product.setId(11L);
        product.setPrice(new BigDecimal("10.00"));

        RequestItem requestItem = new RequestItem();
        requestItem.setId(1L);
        requestItem.setStatus(initialStatus);
        requestItem.setQuantity(2L);
        requestItem.setProduct(product);

        MedicineRequest request = new MedicineRequest();
        request.setId(8L);
        request.setCustomer(customer);
        request.setItems(List.of(requestItem));

        PharmacyOfferItem offerItem = new PharmacyOfferItem();
        offerItem.setAlternative(alternativeOffer);
        offerItem.setRequestItem(requestItem);
        offerItem.setProduct(product);

        PharmacyOffer offer = new PharmacyOffer();
        offer.setId(5L);
        offer.setItems(List.of(offerItem));

        when(medicineRequestRepository.findDetailedById(8L)).thenReturn(Optional.of(request));

        if (alternativeOffer) {
            lenient().when(pharmacyOfferItemRepository.existsPriorAlternative(1L, 11L, 5L))
                    .thenReturn(priorAlternativeExists);
        }

        ProductSummaryResponse summary = new ProductSummaryResponse(
                11L, "panadol", "بانادول", null, null, null, new BigDecimal("10.00"),
                "paracetamol", null, null, null, "img.png");
        lenient().when(productRepository.findAllLocalized(List.of(11L), "en", "en"))
                .thenReturn(List.of(summary));

        service.updateRequestItemStatuses(8L, offer, "en");

        return requestItem.getStatus();
    }
}
