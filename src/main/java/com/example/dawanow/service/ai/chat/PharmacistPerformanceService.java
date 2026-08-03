package com.example.dawanow.service.ai.chat;

import com.example.dawanow.dtos.response.PharmacistPerformanceEntryResponse;
import com.example.dawanow.dtos.response.PharmacistRankingResponse;
import com.example.dawanow.entity.ChatPerformanceMetric;
import com.example.dawanow.entity.DashboardPeriod;
import com.example.dawanow.entity.Pharmacist;
import com.example.dawanow.entity.Pharmacy;
import com.example.dawanow.entity.User;
import com.example.dawanow.repo.AiPharmacistPerformanceRepository;
import com.example.dawanow.repo.AiPharmacistPerformanceRepository.PharmacistPerformanceProjection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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

    public PerformanceResult rank(User user, ChatPerformanceMetric requestedMetric, DashboardPeriod requestedPeriod) {
        ChatPerformanceMetric metric = requestedMetric == null ? ChatPerformanceMetric.BOTH : requestedMetric;
        DashboardPeriod period = requestedPeriod == null ? DashboardPeriod.LAST_WEEK : requestedPeriod;

        Pharmacist pharmacist = currentAdmin(user);
        if (pharmacist == null) {
            return PerformanceResult.denied(metric, period);
        }
        Pharmacy pharmacy = pharmacist.getPharmacy();

        List<PharmacistRankingResponse> rankings = new ArrayList<>(2);
        LocalDateTime start = period.getStartDateTime();
        LocalDateTime end = period.getEndDateTime();
        if (metric == ChatPerformanceMetric.OFFERS_CREATED || metric == ChatPerformanceMetric.BOTH) {
            addRanking(rankings, ChatPerformanceMetric.OFFERS_CREATED, period,
                    repository.findTopOfferCreators(
                            pharmacy.getId(), pharmacist.getId(), start, end,
                            PageRequest.of(0, MAX_RESULTS)));
        }
        if (metric == ChatPerformanceMetric.SUCCESSFUL_ORDERS || metric == ChatPerformanceMetric.BOTH) {
            addRanking(rankings, ChatPerformanceMetric.SUCCESSFUL_ORDERS, period,
                    repository.findTopSuccessfulOrderCreators(
                            pharmacy.getId(), pharmacist.getId(), start, end,
                            PageRequest.of(0, MAX_RESULTS)));
        }
        return PerformanceResult.allowed(metric, period, pharmacy.getId(), rankings);
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
            List<PharmacistPerformanceProjection> projections
    ) {
        List<PharmacistPerformanceEntryResponse> entries = new ArrayList<>(projections.size());
        for (int index = 0; index < projections.size(); index++) {
            PharmacistPerformanceProjection projection = projections.get(index);
            entries.add(new PharmacistPerformanceEntryResponse(
                    index + 1,
                    projection.getPharmacistId(),
                    projection.getFirstName(),
                    projection.getLastName(),
                    projection.getActivityCount() == null ? 0 : projection.getActivityCount()
            ));
        }
        rankings.add(new PharmacistRankingResponse(metric.name(), period.name(), List.copyOf(entries)));
    }

    public record PerformanceResult(
            boolean authorized,
            ChatPerformanceMetric requestedMetric,
            DashboardPeriod period,
            Long pharmacyId,
            List<PharmacistRankingResponse> rankings
    ) {
        private static PerformanceResult denied(ChatPerformanceMetric metric, DashboardPeriod period) {
            return new PerformanceResult(false, metric, period, null, List.of());
        }

        private static PerformanceResult allowed(
                ChatPerformanceMetric metric,
                DashboardPeriod period,
                Long pharmacyId,
                List<PharmacistRankingResponse> rankings
        ) {
            return new PerformanceResult(true, metric, period, pharmacyId, List.copyOf(rankings));
        }
    }
}
