package com.example.dawanow.service.ai.chat;

import com.example.dawanow.dtos.response.PharmacistPerformanceEntryResponse;
import com.example.dawanow.dtos.response.PharmacistRankingResponse;
import com.example.dawanow.entity.ChatPerformanceDirection;
import com.example.dawanow.entity.ChatPerformanceMetric;
import com.example.dawanow.entity.DashboardPeriod;
import com.example.dawanow.entity.Pharmacist;
import com.example.dawanow.entity.Pharmacy;
import com.example.dawanow.entity.User;
import com.example.dawanow.repo.AiPharmacistPerformanceRepository;
import com.example.dawanow.repo.AiPharmacistPerformanceRepository.PharmacistPerformanceProjection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PharmacistPerformanceService {

    private static final int MAX_RESULTS = 5;

    private final AiPharmacistPerformanceRepository repository;

    public Long currentAdminPharmacyId(User user) {
        Pharmacist pharmacist = currentAdmin(user);
        return pharmacist == null ? null : pharmacist.getPharmacy().getId();
    }

    public PerformanceResult rank(
            User user,
            ChatPerformanceMetric requestedMetric,
            DashboardPeriod requestedPeriod,
            ChatPerformanceDirection requestedDirection
    ) {
        ChatPerformanceMetric metric = requestedMetric == null ? ChatPerformanceMetric.BOTH : requestedMetric;
        DashboardPeriod period = requestedPeriod == null ? DashboardPeriod.LAST_WEEK : requestedPeriod;
        ChatPerformanceDirection direction = requestedDirection == null
                ? ChatPerformanceDirection.TOP
                : requestedDirection;

        Pharmacist pharmacist = currentAdmin(user);
        if (pharmacist == null) {
            return PerformanceResult.denied(metric, period, direction);
        }
        Pharmacy pharmacy = pharmacist.getPharmacy();

        List<PharmacistRankingResponse> rankings = new ArrayList<>(2);
        LocalDateTime start = period.getStartDateTime();
        LocalDateTime end = period.getEndDateTime();
        List<Pharmacist> currentStaff = direction == ChatPerformanceDirection.BOTTOM
                ? repository.findCurrentRegularPharmacists(pharmacy.getId(), pharmacist.getId())
                : List.of();
        if (metric == ChatPerformanceMetric.OFFERS_CREATED || metric == ChatPerformanceMetric.BOTH) {
            List<PharmacistPerformanceProjection> counts = repository.findTopOfferCreators(
                    pharmacy.getId(), pharmacist.getId(), start, end,
                    direction == ChatPerformanceDirection.TOP
                            ? PageRequest.of(0, MAX_RESULTS)
                            : Pageable.unpaged());
            addRanking(rankings, ChatPerformanceMetric.OFFERS_CREATED, period, direction,
                    rankedEntries(direction, currentStaff, counts));
        }
        if (metric == ChatPerformanceMetric.SUCCESSFUL_ORDERS || metric == ChatPerformanceMetric.BOTH) {
            List<PharmacistPerformanceProjection> counts = repository.findTopSuccessfulOrderCreators(
                    pharmacy.getId(), pharmacist.getId(), start, end,
                    direction == ChatPerformanceDirection.TOP
                            ? PageRequest.of(0, MAX_RESULTS)
                            : Pageable.unpaged());
            addRanking(rankings, ChatPerformanceMetric.SUCCESSFUL_ORDERS, period, direction,
                    rankedEntries(direction, currentStaff, counts));
        }
        return PerformanceResult.allowed(metric, period, direction, pharmacy.getId(), rankings);
    }

    private Pharmacist currentAdmin(User user) {
        if (!(user instanceof Pharmacist) || user.getId() == null) {
            return null;
        }
        Pharmacist pharmacist = repository.findById(user.getId()).orElse(null);
        if (pharmacist == null) {
            return null;
        }
        Pharmacy pharmacy = pharmacist.getPharmacy();
        if (pharmacy == null
                || pharmacy.getAdminPharmacist() == null
                || !pharmacist.getId().equals(pharmacy.getAdminPharmacist().getId())) {
            return null;
        }
        return pharmacist;
    }

    private void addRanking(
            List<PharmacistRankingResponse> rankings,
            ChatPerformanceMetric metric,
            DashboardPeriod period,
            ChatPerformanceDirection direction,
            List<PerformanceEntry> performanceEntries
    ) {
        List<PharmacistPerformanceEntryResponse> entries = new ArrayList<>(performanceEntries.size());
        for (int index = 0; index < performanceEntries.size(); index++) {
            PerformanceEntry entry = performanceEntries.get(index);
            entries.add(new PharmacistPerformanceEntryResponse(
                    index + 1,
                    entry.pharmacistId(),
                    entry.firstName(),
                    entry.lastName(),
                    entry.count()
            ));
        }
        rankings.add(new PharmacistRankingResponse(
                metric.name(), period.name(), direction.name(), List.copyOf(entries)));
    }

    private List<PerformanceEntry> rankedEntries(
            ChatPerformanceDirection direction,
            List<Pharmacist> currentStaff,
            List<PharmacistPerformanceProjection> counts
    ) {
        if (direction == ChatPerformanceDirection.TOP) {
            return counts.stream()
                    .map(this::toEntry)
                    .toList();
        }

        Map<Long, Long> countByPharmacistId = new HashMap<>();
        for (PharmacistPerformanceProjection count : counts) {
            countByPharmacistId.put(
                    count.getPharmacistId(),
                    count.getActivityCount() == null ? 0L : count.getActivityCount()
            );
        }
        return currentStaff.stream()
                .map(pharmacist -> new PerformanceEntry(
                        pharmacist.getId(),
                        pharmacist.getFirstName(),
                        pharmacist.getLastName(),
                        countByPharmacistId.getOrDefault(pharmacist.getId(), 0L)
                ))
                .sorted(Comparator.comparingLong(PerformanceEntry::count)
                        .thenComparingLong(PerformanceEntry::pharmacistId))
                .limit(MAX_RESULTS)
                .toList();
    }

    private PerformanceEntry toEntry(PharmacistPerformanceProjection projection) {
        return new PerformanceEntry(
                projection.getPharmacistId(),
                projection.getFirstName(),
                projection.getLastName(),
                projection.getActivityCount() == null ? 0L : projection.getActivityCount()
        );
    }

    private record PerformanceEntry(
            Long pharmacistId,
            String firstName,
            String lastName,
            long count
    ) {
    }

    public record PerformanceResult(
            boolean authorized,
            ChatPerformanceMetric requestedMetric,
            DashboardPeriod period,
            ChatPerformanceDirection direction,
            Long pharmacyId,
            List<PharmacistRankingResponse> rankings
    ) {
        private static PerformanceResult denied(
                ChatPerformanceMetric metric,
                DashboardPeriod period,
                ChatPerformanceDirection direction
        ) {
            return new PerformanceResult(false, metric, period, direction, null, List.of());
        }

        private static PerformanceResult allowed(
                ChatPerformanceMetric metric,
                DashboardPeriod period,
                ChatPerformanceDirection direction,
                Long pharmacyId,
                List<PharmacistRankingResponse> rankings
        ) {
            return new PerformanceResult(
                    true, metric, period, direction, pharmacyId, List.copyOf(rankings));
        }
    }
}
