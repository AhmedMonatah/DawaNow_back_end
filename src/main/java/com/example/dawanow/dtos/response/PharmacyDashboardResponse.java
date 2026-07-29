package com.example.dawanow.dtos.response;

import java.math.BigDecimal;
import java.util.List;

public record PharmacyDashboardResponse(
        BigDecimal totalRevenue,
        long totalOrders,
        long requestsReceived,
        long offersCreated,
        List<MostSoldProductResponse> topSellingProducts,
        List<OrderResponse> recentOrders
) {
}
