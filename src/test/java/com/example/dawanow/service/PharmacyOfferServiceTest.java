package com.example.dawanow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.dawanow.dtos.response.PharmacyOfferItemResponse;
import com.example.dawanow.dtos.response.PharmacyOfferResponse;
import com.example.dawanow.dtos.response.ProductSummaryResponse;
import com.example.dawanow.entity.PharmacyOffer;
import com.example.dawanow.mapper.PharmacyOfferMapper;
import com.example.dawanow.repo.PharmacyAssignmentRepository;
import com.example.dawanow.repo.PharmacyOfferItemRepository;
import com.example.dawanow.repo.PharmacyOfferRepository;
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
class PharmacyOfferServiceTest {

    @Mock
    private MedicineRequestService medicineRequestService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private PharmacyAssignmentRepository pharmacyAssignmentRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private RequestItemRepository requestItemRepository;
    @Mock
    private PharmacyOfferRepository pharmacyOfferRepository;
    @Mock
    private PharmacyOfferItemRepository pharmacyOfferItemRepository;
    @Mock
    private PharmacyOfferMapper pharmacyOfferMapper;

    private PharmacyOfferService service;

    @BeforeEach
    void setUp() {
        service = new PharmacyOfferService(
                medicineRequestService, currentUserProvider, pharmacyAssignmentRepository,
                productRepository, requestItemRepository, pharmacyOfferRepository,
                pharmacyOfferItemRepository, pharmacyOfferMapper
        );
    }

    @Test
    void resolvesLocalizedProductsForOfferItems() {
        PharmacyOffer offer = new PharmacyOffer();
        offer.setId(9L);
        when(pharmacyOfferRepository.findById(9L)).thenReturn(Optional.of(offer));

        PharmacyOfferItemResponse item = new PharmacyOfferItemResponse(1L, 9L, 2L, 11L);
        when(pharmacyOfferItemRepository.findByOfferIdIn(List.of(9L))).thenReturn(List.of(item));

        ProductSummaryResponse summary = new ProductSummaryResponse(
                11L, "بانادول", "بانادول", null, null, null, new BigDecimal("10.00"),
                "باراسيتامول", null, null, null, "img.png");
        when(productRepository.findAllLocalized(List.of(11L), "ar", "en")).thenReturn(List.of(summary));

        PharmacyOfferResponse expected =
                new PharmacyOfferResponse(9L, 1L, 1L, 1L, null, 2.5, List.of(item));
        when(pharmacyOfferMapper.toResponse(offer, List.of(item))).thenReturn(expected);

        PharmacyOfferResponse result = service.getOfferById(9L, "ar");

        assertThat(result).isEqualTo(expected);
        assertThat(item.getProduct()).isSameAs(summary);
    }
}
