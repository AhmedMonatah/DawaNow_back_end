package com.example.dawanow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.dawanow.dtos.response.MedicineRequestItemResponse;
import com.example.dawanow.dtos.response.MedicineRequestResponse;
import com.example.dawanow.dtos.response.ProductSummaryResponse;
import com.example.dawanow.entity.Customer;
import com.example.dawanow.entity.MedicineRequest;
import com.example.dawanow.mapper.MedicineRequestMapper;
import com.example.dawanow.mapper.MedicineRequestResultItemMapper;
import com.example.dawanow.repo.MedicineRequestRepository;
import com.example.dawanow.repo.PharmacyAssignmentRepository;
import com.example.dawanow.repo.PharmacyOfferItemRepository;
import com.example.dawanow.repo.PharmacyRepository;
import com.example.dawanow.repo.ProductRepository;
import com.example.dawanow.repo.RequestItemRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    private MedicineRequestService service;

    @BeforeEach
    void setUp() {
        service = new MedicineRequestService(
                medicineRequestRepository, pharmacyRepository, currentUserProvider,
                medicineRequestMapper, cartService, assignmentService,
                pharmacyAssignmentRepository, fileStorageService, productService,
                pharmacyOfferItemRepository, medicineRequestResultItemMapper,
                requestItemRepository, productRepository
        );
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
                8L, 3L, "name", "phone", null, null, null, null, null, List.of(item), null, null);
        when(medicineRequestMapper.toResponse(request, List.of(item))).thenReturn(expected);

        MedicineRequestResponse result = service.getRequestById(8L, "ar");

        assertThat(result).isEqualTo(expected);
        assertThat(item.getProduct()).isSameAs(summary);
        assertThat(item.getUnitPrice()).isEqualTo(10.0);
    }
}
