package com.example.dawanow.service.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.dawanow.dtos.response.PharmacistPerformanceEntryResponse;
import com.example.dawanow.entity.ChatPerformanceDirection;
import com.example.dawanow.entity.ChatPerformanceMetric;
import com.example.dawanow.entity.DashboardPeriod;
import com.example.dawanow.entity.Pharmacist;
import com.example.dawanow.entity.Pharmacy;
import com.example.dawanow.entity.User;
import com.example.dawanow.repo.AiPharmacistPerformanceRepository;
import com.example.dawanow.repo.AiPharmacistPerformanceRepository.PharmacistPerformanceProjection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PharmacistPerformanceServiceTest {

    @Mock
    private AiPharmacistPerformanceRepository repository;

    private PharmacistPerformanceService service;

    @BeforeEach
    void setUp() {
        service = new PharmacistPerformanceService(repository);
    }

    @Test
    void customerIsDeniedWithoutReadingPerformanceData() {
        User customer = new User();
        customer.setId(1L);

        var result = service.rank(
                customer, ChatPerformanceMetric.BOTH, DashboardPeriod.LAST_WEEK, ChatPerformanceDirection.TOP);

        assertThat(result.authorized()).isFalse();
        assertThat(result.rankings()).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void regularPharmacistIsDeniedWithoutReadingPerformanceData() {
        Pharmacist admin = pharmacist(1L, "Admin", "Owner");
        Pharmacist regular = pharmacist(2L, "Regular", "Member");
        Pharmacy pharmacy = pharmacy(10L, admin);
        regular.setPharmacy(pharmacy);
        when(repository.findById(2L)).thenReturn(Optional.of(regular));

        var result = service.rank(regular, ChatPerformanceMetric.OFFERS_CREATED,
                DashboardPeriod.LAST_MONTH, ChatPerformanceDirection.BOTTOM);

        assertThat(result.authorized()).isFalse();
        assertThat(result.rankings()).isEmpty();
        verify(repository).findById(2L);
        verify(repository, never()).findTopOfferCreators(any(), any(), any(), any(), any());
        verify(repository, never()).findTopSuccessfulOrderCreators(any(), any(), any(), any(), any());
    }

    @Test
    void onlyCurrentPharmacyAdminExposesItsPharmacyId() {
        Pharmacist admin = pharmacist(1L, "Admin", "Owner");
        Pharmacist regular = pharmacist(2L, "Regular", "Member");
        Pharmacy pharmacy = pharmacy(10L, admin);
        regular.setPharmacy(pharmacy);
        when(repository.findById(1L)).thenReturn(Optional.of(admin));
        when(repository.findById(2L)).thenReturn(Optional.of(regular));

        assertThat(service.currentAdminPharmacyId(admin)).isEqualTo(10L);
        assertThat(service.currentAdminPharmacyId(regular)).isNull();
    }

    @Test
    void adminCanRequestBothRankingsWithDefaultPeriodAndTopFiveLimit() {
        Pharmacist admin = pharmacist(1L, "Admin", "Owner");
        pharmacy(10L, admin);
        when(repository.findById(1L)).thenReturn(Optional.of(admin));
        when(repository.findTopOfferCreators(eq(10L), eq(1L), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(projection(2L, "Mona", "Ali", 8L)));
        when(repository.findTopSuccessfulOrderCreators(eq(10L), eq(1L), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(projection(3L, "Omar", "Hassan", 5L)));

        var result = service.rank(admin, null, null, null);

        assertThat(result.authorized()).isTrue();
        assertThat(result.requestedMetric()).isEqualTo(ChatPerformanceMetric.BOTH);
        assertThat(result.period()).isEqualTo(DashboardPeriod.LAST_WEEK);
        assertThat(result.direction()).isEqualTo(ChatPerformanceDirection.TOP);
        assertThat(result.pharmacyId()).isEqualTo(10L);
        assertThat(result.rankings()).extracting(ranking -> ranking.metric())
                .containsExactly("OFFERS_CREATED", "SUCCESSFUL_ORDERS");
        assertThat(result.rankings().getFirst().entries().getFirst().rank()).isEqualTo(1);
        assertThat(result.rankings().getFirst().entries().getFirst().count()).isEqualTo(8L);

        verify(repository).findTopOfferCreators(eq(10L), eq(1L), any(), any(),
                org.mockito.ArgumentMatchers.argThat(pageable -> pageable.getPageSize() == 5));
        verify(repository).findTopSuccessfulOrderCreators(eq(10L), eq(1L), any(), any(),
                org.mockito.ArgumentMatchers.argThat(pageable -> pageable.getPageSize() == 5));
    }

    @Test
    void singleMetricPreservesAnEmptyRankingAndDoesNotQueryTheOtherAggregate() {
        Pharmacist admin = pharmacist(1L, "Admin", "Owner");
        pharmacy(10L, admin);
        when(repository.findById(1L)).thenReturn(Optional.of(admin));
        when(repository.findTopOfferCreators(eq(10L), eq(1L), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

        var result = service.rank(admin, ChatPerformanceMetric.OFFERS_CREATED,
                DashboardPeriod.LAST_DAY, ChatPerformanceDirection.TOP);

        assertThat(result.rankings()).hasSize(1);
        assertThat(result.rankings().getFirst().metric()).isEqualTo("OFFERS_CREATED");
        assertThat(result.rankings().getFirst().entries()).isEmpty();
        verify(repository, never()).findTopSuccessfulOrderCreators(any(), any(), any(), any(), any());
    }

    @Test
    void bothPreservesEachRankingWhenOnlyOneMetricHasActivity() {
        Pharmacist admin = pharmacist(1L, "Admin", "Owner");
        pharmacy(10L, admin);
        when(repository.findById(1L)).thenReturn(Optional.of(admin));
        when(repository.findTopOfferCreators(eq(10L), eq(1L), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(projection(2L, "Mona", "Ali", 8L)));
        when(repository.findTopSuccessfulOrderCreators(eq(10L), eq(1L), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

        var result = service.rank(admin, ChatPerformanceMetric.BOTH,
                DashboardPeriod.LAST_WEEK, ChatPerformanceDirection.TOP);

        assertThat(result.rankings()).extracting(ranking -> ranking.metric())
                .containsExactly("OFFERS_CREATED", "SUCCESSFUL_ORDERS");
        assertThat(result.rankings().getFirst().entries()).hasSize(1);
        assertThat(result.rankings().get(1).entries()).isEmpty();
    }

    @Test
    void bottomRankingIncludesZeroActivityStaffAndUsesIdForTies() {
        Pharmacist admin = pharmacist(1L, "Admin", "Owner");
        pharmacy(10L, admin);
        Pharmacist zeroLater = pharmacist(5L, "Ziad", "Late");
        Pharmacist active = pharmacist(3L, "Mona", "Ali");
        Pharmacist zeroEarlier = pharmacist(2L, "Omar", "Early");
        when(repository.findById(1L)).thenReturn(Optional.of(admin));
        when(repository.findCurrentRegularPharmacists(10L, 1L))
                .thenReturn(List.of(zeroLater, active, zeroEarlier));
        when(repository.findTopOfferCreators(eq(10L), eq(1L), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(projection(3L, "Mona", "Ali", 4L)));

        var result = service.rank(admin, ChatPerformanceMetric.OFFERS_CREATED,
                DashboardPeriod.LAST_MONTH, ChatPerformanceDirection.BOTTOM);

        assertThat(result.direction()).isEqualTo(ChatPerformanceDirection.BOTTOM);
        assertThat(result.rankings().getFirst().direction()).isEqualTo("BOTTOM");
        assertThat(result.rankings().getFirst().entries())
                .extracting(PharmacistPerformanceEntryResponse::pharmacistId)
                .containsExactly(2L, 5L, 3L);
        assertThat(result.rankings().getFirst().entries())
                .extracting(PharmacistPerformanceEntryResponse::count)
                .containsExactly(0L, 0L, 4L);
        verify(repository).findTopOfferCreators(eq(10L), eq(1L), any(), any(),
                org.mockito.ArgumentMatchers.argThat(Pageable::isUnpaged));
    }

    @Test
    void bottomRankingIsLimitedToFiveCurrentStaffMembers() {
        Pharmacist admin = pharmacist(1L, "Admin", "Owner");
        pharmacy(10L, admin);
        List<Pharmacist> staff = List.of(
                pharmacist(2L, "A", "Two"),
                pharmacist(3L, "B", "Three"),
                pharmacist(4L, "C", "Four"),
                pharmacist(5L, "D", "Five"),
                pharmacist(6L, "E", "Six"),
                pharmacist(7L, "F", "Seven")
        );
        when(repository.findById(1L)).thenReturn(Optional.of(admin));
        when(repository.findCurrentRegularPharmacists(10L, 1L)).thenReturn(staff);
        when(repository.findTopSuccessfulOrderCreators(
                eq(10L), eq(1L), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

        var result = service.rank(admin, ChatPerformanceMetric.SUCCESSFUL_ORDERS,
                DashboardPeriod.LAST_WEEK, ChatPerformanceDirection.BOTTOM);

        assertThat(result.rankings().getFirst().entries())
                .extracting(PharmacistPerformanceEntryResponse::pharmacistId)
                .containsExactly(2L, 3L, 4L, 5L, 6L);
    }

    private Pharmacy pharmacy(Long id, Pharmacist admin) {
        Pharmacy pharmacy = new Pharmacy();
        pharmacy.setId(id);
        pharmacy.setAdminPharmacist(admin);
        admin.setPharmacy(pharmacy);
        admin.setAdministeredPharmacy(pharmacy);
        return pharmacy;
    }

    private Pharmacist pharmacist(Long id, String firstName, String lastName) {
        Pharmacist pharmacist = new Pharmacist();
        pharmacist.setId(id);
        pharmacist.setFirstName(firstName);
        pharmacist.setLastName(lastName);
        return pharmacist;
    }

    private PharmacistPerformanceProjection projection(
            Long id,
            String firstName,
            String lastName,
            Long count
    ) {
        return new PharmacistPerformanceProjection() {
            @Override
            public Long getPharmacistId() {
                return id;
            }

            @Override
            public String getFirstName() {
                return firstName;
            }

            @Override
            public String getLastName() {
                return lastName;
            }

            @Override
            public Long getActivityCount() {
                return count;
            }
        };
    }
}
