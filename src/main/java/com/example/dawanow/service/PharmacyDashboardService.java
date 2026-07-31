package com.example.dawanow.service;

import com.example.dawanow.dtos.response.MostSoldProductResponse;
import com.example.dawanow.dtos.response.OrderResponse;
import com.example.dawanow.dtos.response.PharmacyDashboardResponse;
import com.example.dawanow.entity.DashboardPeriod;
import com.example.dawanow.entity.Pharmacist;
import com.example.dawanow.entity.Pharmacy;
import com.example.dawanow.exception.ResourceNotFoundException;
import com.example.dawanow.mapper.OrderMapper;
import com.example.dawanow.repo.OrderRepository;
import com.example.dawanow.repo.PharmacyAssignmentRepository;
import com.example.dawanow.repo.PharmacyOfferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PharmacyDashboardService {

    private final CurrentPharmacistProvider currentPharmacistProvider;
    private final OrderRepository orderRepository;
    private final PharmacyAssignmentRepository pharmacyAssignmentRepository;
    private final PharmacyOfferRepository pharmacyOfferRepository;
    private final OrderMapper orderMapper;

    public PharmacyDashboardResponse getDashboard(DashboardPeriod period) {
        Pharmacist current = currentPharmacistProvider.get();
        Pharmacy pharmacy = current.getPharmacy();
        if (pharmacy == null) {
            throw new ResourceNotFoundException("You are not assigned to any pharmacy");
        }
        if (!pharmacy.getAdminPharmacist().getId().equals(current.getId())) {
            throw new AccessDeniedException("Only the pharmacy admin can access the dashboard");
        }

        var dateTimeStart = period.getStartDateTime();
        var dateTimeEnd = period.getEndDateTime();

        BigDecimal totalRevenue = orderRepository.sumTotalPriceByPharmacyIdAndDateBetween(
                pharmacy.getId(), dateTimeStart, dateTimeEnd);

        long totalOrders = orderRepository.countByPharmacyIdAndDateBetween(
                pharmacy.getId(), dateTimeStart, dateTimeEnd);

        long requestsReceived = pharmacyAssignmentRepository.countByPharmacyIdAndAssignedAtBetween(
                pharmacy.getId(), dateTimeStart, dateTimeEnd);

        long offersCreated = pharmacyOfferRepository.countByPharmacyIdAndCreatedAtBetween(
                pharmacy.getId(), dateTimeStart, dateTimeEnd);

        List<MostSoldProductResponse> topSellingProducts = orderRepository
                .findTopSellingProducts(pharmacy.getId(), dateTimeStart, dateTimeEnd, PageRequest.of(0, 5))
                .stream()
                .map(row -> new MostSoldProductResponse(
                        (Long) row[0],
                        (String) row[1],
                        (String) row[2],
                        (Long) row[3],
                        (BigDecimal) row[4]
                ))
                .toList();

        List<OrderResponse> recentOrders = orderRepository
                .findByPharmacyIdAndDateBetweenOrderByDateDesc(pharmacy.getId(), dateTimeStart, dateTimeEnd)
                .stream()
                .limit(5)
                .map(orderMapper::toResponse)
                .toList();

        return new PharmacyDashboardResponse(
                totalRevenue, totalOrders, requestsReceived, offersCreated,
                topSellingProducts, recentOrders
        );
    }
}
