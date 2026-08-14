package com.example.dawanow.service.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.dawanow.entity.Pharmacist;
import com.example.dawanow.entity.Pharmacy;
import com.example.dawanow.repo.AiPharmacyAnalyticsRepository;
import com.example.dawanow.repo.AiPharmacyAnalyticsRepository.PharmacistCountProjection;
import com.example.dawanow.repo.AiPharmacyAnalyticsRepository.OfferStatusCountProjection;
import com.example.dawanow.repo.AiPharmacyAnalyticsRepository.OrderStatusCountProjection;
import com.example.dawanow.service.ai.chat.PharmacyAnalyticsService.AnalyticsResult;
import com.example.dawanow.service.ai.chat.AiChatModelClient.AnalyticsSpec;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PharmacyAnalyticsServiceTest {

    @Mock
    private AiPharmacyAnalyticsRepository repository;

    private PharmacyAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new PharmacyAnalyticsService(repository);
    }

    @Test
    void regularPharmacistCanUseSelfOrdersPresetWithoutGatewayData() {
        Pharmacist pharmacist = pharmacist(11L, false);
        stubCaller(pharmacist);
        stubEmptyOrderAggregates();

        AnalyticsResult result = service.analyze(
                pharmacist, "SELF_MONTH_ORDERS", null);

        assertThat(result.status()).isEqualTo(AnalyticsResult.Status.ALLOWED);
        assertThat(result.scope()).isEqualTo("SELF");
        assertThat(result.analytics().metrics())
                .singleElement()
                .satisfies(metric -> {
                    assertThat(metric.key()).isEqualTo("ORDERS_GENERATED");
                    assertThat(metric.value()).isEqualByComparingTo(BigDecimal.ZERO);
                });
        verify(repository, never()).countRequests(anyLong(), any(), any());
    }

    @Test
    void regularPharmacistCannotUseAdminPreset() {
        Pharmacist pharmacist = pharmacist(11L, false);
        stubCaller(pharmacist);

        AnalyticsResult result = service.analyze(
                pharmacist, "PHARMACY_MONTH_OVERVIEW", null);

        assertThat(result.status()).isEqualTo(AnalyticsResult.Status.DENIED);
        verify(repository, never()).countOffersByStatus(anyLong(), any(), any(), any());
    }

    @Test
    void adminOverviewIncludesWholePharmacyRequestsAndRevenue() {
        Pharmacist admin = pharmacist(7L, true);
        stubCaller(admin);
        when(repository.countRequests(anyLong(), any(), any())).thenReturn(10L);
        when(repository.countCoveredRequests(anyLong(), any(), any())).thenReturn(8L);
        List<OfferStatusCountProjection> offerCounts = List.of(
                offerStatus("ACCEPTED", 4L), offerStatus("REJECTED", 2L));
        List<OrderStatusCountProjection> orderCounts = List.of(orderStatus("DELIVERED", 3L));
        when(repository.countOffersByStatus(anyLong(), isNull(), any(), any()))
                .thenReturn(offerCounts);
        when(repository.countOrdersByStatus(anyLong(), isNull(), any(), any()))
                .thenReturn(orderCounts);
        when(repository.sumOrderValue(anyLong(), isNull(), any(), any()))
                .thenReturn(new BigDecimal("500.00"));
        when(repository.sumDeliveredRevenue(anyLong(), isNull(), any(), any()))
                .thenReturn(new BigDecimal("420.00"));
        when(repository.averageOrderValue(anyLong(), isNull(), any(), any()))
                .thenReturn(new BigDecimal("166.67"));
        when(repository.findCurrentPharmacists(anyLong())).thenReturn(List.of(admin));
        when(repository.countGeneratedOrdersByRegularPharmacist(anyLong(), anyLong(), any(), any()))
                .thenReturn(List.of());
        when(repository.findLargestOrders(anyLong(), isNull(), any(), any(), any()))
                .thenReturn(List.of());
        when(repository.findTopDeliveredProducts(anyLong(), isNull(), any(), any(), isNull(), any()))
                .thenReturn(List.of());

        AnalyticsResult result = service.analyze(
                admin, "PHARMACY_MONTH_OVERVIEW", null);

        assertThat(result.status()).isEqualTo(AnalyticsResult.Status.ALLOWED);
        assertThat(result.analytics().metrics())
                .anySatisfy(metric -> {
                    assertThat(metric.key()).isEqualTo("REQUEST_COVERAGE_RATE");
                    assertThat(metric.value()).isEqualByComparingTo("80.0");
                })
                .anySatisfy(metric -> {
                    assertThat(metric.key()).isEqualTo("DELIVERED_REVENUE");
                    assertThat(metric.value()).isEqualByComparingTo("420.00");
                });
    }

    @Test
    void topEmployeeDefaultsToOrdersGeneratedAndIncludesZeroActivityStaff() {
        Pharmacist admin = pharmacist(7L, true);
        Pharmacist active = teammate(12L, "Ahmed", "Ali", admin.getPharmacy());
        Pharmacist idle = teammate(13L, "Mona", "Hassan", admin.getPharmacy());
        stubCaller(admin);
        stubEmptyOrderAggregates();
        when(repository.findCurrentPharmacists(anyLong())).thenReturn(List.of(admin, active, idle));
        PharmacistCountProjection count = mock(PharmacistCountProjection.class);
        when(count.getPharmacistId()).thenReturn(active.getId());
        when(count.getActivityCount()).thenReturn(5L);
        when(repository.countGeneratedOrdersByRegularPharmacist(anyLong(), anyLong(), any(), any()))
                .thenReturn(List.of(count));

        AnalyticsResult result = service.analyze(
                admin, "PHARMACY_MONTH_TOP_EMPLOYEE", null);

        assertThat(result.analytics().rankings())
                .extracting(entry -> entry.pharmacistId() + ":" + entry.count())
                .containsExactly("12:5", "13:0");
    }

    @Test
    void comparisonUsesPreviousEqualDurationAndReturnsDelta() {
        Pharmacist pharmacist = pharmacist(11L, false);
        stubCaller(pharmacist);
        List<OrderStatusCountProjection> current = List.of(orderStatus("DELIVERED", 4L));
        List<OrderStatusCountProjection> previous = List.of(orderStatus("DELIVERED", 2L));
        when(repository.countOffersByStatus(anyLong(), any(), any(), any())).thenReturn(List.of());
        when(repository.countOrdersByStatus(anyLong(), any(), any(), any()))
                .thenReturn(current, previous);
        when(repository.sumOrderValue(anyLong(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(repository.sumDeliveredRevenue(anyLong(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(repository.averageOrderValue(anyLong(), any(), any(), any())).thenReturn(BigDecimal.ZERO);

        AnalyticsResult result = service.analyze(pharmacist, null, new AnalyticsSpec(
                "ORDERS_GENERATED", "SELF", "THIS_MONTH", null, null,
                null, null, null, "PREVIOUS_PERIOD"));

        assertThat(result.analytics().metrics())
                .singleElement()
                .satisfies(metric -> {
                    assertThat(metric.value()).isEqualByComparingTo("4");
                    assertThat(metric.previousValue()).isEqualByComparingTo("2");
                    assertThat(metric.deltaPercent()).isEqualByComparingTo("100.0");
                });
    }

    private void stubEmptyOrderAggregates() {
        when(repository.countOffersByStatus(anyLong(), any(), any(), any())).thenReturn(List.of());
        when(repository.countOrdersByStatus(anyLong(), any(), any(), any())).thenReturn(List.of());
        when(repository.sumOrderValue(anyLong(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(repository.sumDeliveredRevenue(anyLong(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(repository.averageOrderValue(anyLong(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
    }

    private void stubCaller(Pharmacist pharmacist) {
        when(repository.findById(pharmacist.getId())).thenReturn(Optional.of(pharmacist));
    }

    private Pharmacist pharmacist(Long id, boolean admin) {
        Pharmacist pharmacist = new Pharmacist();
        pharmacist.setId(id);
        pharmacist.setFirstName(admin ? "Admin" : "Staff");
        pharmacist.setLastName("User");
        Pharmacy pharmacy = new Pharmacy();
        pharmacy.setId(3L);
        pharmacist.setPharmacy(pharmacy);
        if (admin) pharmacy.setAdminPharmacist(pharmacist);
        else {
            Pharmacist owner = new Pharmacist();
            owner.setId(99L);
            pharmacy.setAdminPharmacist(owner);
        }
        return pharmacist;
    }

    private Pharmacist teammate(Long id, String firstName, String lastName, Pharmacy pharmacy) {
        Pharmacist pharmacist = new Pharmacist();
        pharmacist.setId(id);
        pharmacist.setFirstName(firstName);
        pharmacist.setLastName(lastName);
        pharmacist.setPharmacy(pharmacy);
        return pharmacist;
    }

    private OfferStatusCountProjection offerStatus(String name, long count) {
        OfferStatusCountProjection projection = mock(OfferStatusCountProjection.class);
        when(projection.getStatus()).thenReturn(com.example.dawanow.entity.OfferStatus.valueOf(name));
        when(projection.getActivityCount()).thenReturn(count);
        return projection;
    }

    private OrderStatusCountProjection orderStatus(String name, long count) {
        OrderStatusCountProjection projection = mock(OrderStatusCountProjection.class);
        when(projection.getStatus()).thenReturn(com.example.dawanow.entity.OrderStatus.valueOf(name));
        when(projection.getActivityCount()).thenReturn(count);
        return projection;
    }
}
