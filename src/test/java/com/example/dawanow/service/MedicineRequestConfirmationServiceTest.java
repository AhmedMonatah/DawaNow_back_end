package com.example.dawanow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.dawanow.dtos.request.ConfirmSelectionRequest;
import com.example.dawanow.entity.Customer;
import com.example.dawanow.entity.MasterOrder;
import com.example.dawanow.entity.MedicineRequest;
import com.example.dawanow.entity.OfferStatus;
import com.example.dawanow.entity.Pharmacist;
import com.example.dawanow.entity.Pharmacy;
import com.example.dawanow.entity.PharmacyOffer;
import com.example.dawanow.entity.PharmacyOfferItem;
import com.example.dawanow.entity.Product;
import com.example.dawanow.entity.RequestItem;
import com.example.dawanow.entity.RequestStatus;
import com.example.dawanow.factory.NotificationFactory;
import com.example.dawanow.repo.MasterOrderRepository;
import com.example.dawanow.repo.MedicineRequestRepository;
import com.example.dawanow.repo.OrderRepository;
import com.example.dawanow.repo.PharmacyOfferItemRepository;
import com.example.dawanow.repo.PharmacyOfferRepository;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MedicineRequestConfirmationServiceTest {

    @Mock
    private MedicineRequestRepository medicineRequestRepository;
    @Mock
    private PharmacyOfferRepository pharmacyOfferRepository;
    @Mock
    private PharmacyOfferItemRepository pharmacyOfferItemRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private MasterOrderRepository masterOrderRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private PharmacySelectionOptimizer selectionOptimizer;
    @Mock
    private NotificationService notificationService;
    @Mock
    private NotificationFactory notificationFactory;
    @Mock
    private RequestResultSseService requestResultSseService;

    private MedicineRequestConfirmationService service;

    @BeforeEach
    void setUp() {
        service = new MedicineRequestConfirmationService(
                medicineRequestRepository, pharmacyOfferRepository,
                pharmacyOfferItemRepository, orderRepository, masterOrderRepository,
                currentUserProvider, selectionOptimizer, notificationService,
                notificationFactory, requestResultSseService
        );
    }

    @Test
    void confirmChargesSingleDeliveryFeeBasedOnPharmacyCount() {
        Customer customer = customer(1L);
        MedicineRequest request = request(customer, 7L);

        Product p1 = product(11L, "10.00");
        Product p2 = product(12L, "5.00");
        RequestItem ri1 = requestItem(21L, p1, 2L);
        RequestItem ri2 = requestItem(22L, p2, 1L);

        Pharmacy ph1 = pharmacy(1L);
        Pharmacist pharmacist1 = pharmacist(31L, ph1);
        Pharmacy ph2 = pharmacy(2L);
        Pharmacist pharmacist2 = pharmacist(32L, ph2);

        PharmacyOffer offer1 = offer(41L, request, ph1, pharmacist1);
        PharmacyOffer offer2 = offer(42L, request, ph2, pharmacist2);

        PharmacyOfferItem oi1 = offerItem(51L, offer1, ri1, p1);
        PharmacyOfferItem oi2 = offerItem(52L, offer2, ri2, p2);

        ConfirmSelectionRequest selection = new ConfirmSelectionRequest(List.of(
                new ConfirmSelectionRequest.SelectedItem(21L, 11L),
                new ConfirmSelectionRequest.SelectedItem(22L, 12L)
        ));

        when(currentUserProvider.get()).thenReturn(customer);
        when(medicineRequestRepository.findDetailedById(7L)).thenReturn(Optional.of(request));
        when(masterOrderRepository.existsByRequestId(7L)).thenReturn(false);
        when(pharmacyOfferItemRepository.findByRequestItemIdIn(new LinkedHashSet<>(List.of(21L, 22L))))
                .thenReturn(List.of(oi1, oi2));
        when(selectionOptimizer.optimize(List.of(oi1, oi2))).thenReturn(List.of(oi1, oi2));
        when(pharmacyOfferRepository.findByRequestId(7L)).thenReturn(List.of());

        service.confirm(7L, selection);

        ArgumentCaptor<MasterOrder> captor = ArgumentCaptor.forClass(MasterOrder.class);
        verify(masterOrderRepository).save(captor.capture());

        MasterOrder masterOrder = captor.getValue();
        assertThat(masterOrder.getOrders()).hasSize(2);
        // fee(2) = 15 + 2*5 + floor(2/3)*10 = 25
        assertThat(masterOrder.getDeliveryFee()).isEqualByComparingTo("25");
        // subtotals 20 + 5, plus delivery fee 25
        assertThat(masterOrder.getTotalPrice()).isEqualByComparingTo("50");
    }

    private static Customer customer(Long id) {
        Customer customer = new Customer();
        customer.setId(id);
        return customer;
    }

    private static MedicineRequest request(Customer customer, Long id) {
        MedicineRequest request = new MedicineRequest();
        request.setId(id);
        request.setCustomer(customer);
        request.setStatus(RequestStatus.PENDING);
        request.setDeliveryLatitude(30.0);
        request.setDeliveryLongitude(31.0);
        request.setDeliveryAddress("addr");
        return request;
    }

    private static Product product(Long id, String price) {
        Product product = new Product();
        product.setId(id);
        product.setPrice(new BigDecimal(price));
        return product;
    }

    private static RequestItem requestItem(Long id, Product product, Long quantity) {
        RequestItem requestItem = new RequestItem();
        requestItem.setId(id);
        requestItem.setProduct(product);
        requestItem.setQuantity(quantity);
        return requestItem;
    }

    private static Pharmacy pharmacy(Long id) {
        Pharmacy pharmacy = new Pharmacy();
        pharmacy.setId(id);
        return pharmacy;
    }

    private static Pharmacist pharmacist(Long id, Pharmacy pharmacy) {
        Pharmacist pharmacist = new Pharmacist();
        pharmacist.setId(id);
        pharmacist.setPharmacy(pharmacy);
        return pharmacist;
    }

    private static PharmacyOffer offer(Long id, MedicineRequest request, Pharmacy pharmacy, Pharmacist pharmacist) {
        PharmacyOffer offer = new PharmacyOffer();
        offer.setId(id);
        offer.setRequest(request);
        offer.setPharmacy(pharmacy);
        offer.setPharmacist(pharmacist);
        offer.setStatus(OfferStatus.PENDING);
        return offer;
    }

    private static PharmacyOfferItem offerItem(Long id, PharmacyOffer offer, RequestItem requestItem, Product product) {
        PharmacyOfferItem item = new PharmacyOfferItem();
        item.setId(id);
        item.setOffer(offer);
        item.setRequestItem(requestItem);
        item.setProduct(product);
        item.setAlternative(false);
        return item;
    }
}