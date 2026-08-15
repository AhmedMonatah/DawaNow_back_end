package com.example.dawanow.dtos.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Versioned, read-only pharmacy analytics snapshot returned by AI chat.
 * Keys are stable enums-as-strings so mobile clients can localize their own UI.
 */
public record ChatAnalyticsResponse(
        int schemaVersion,
        String scope,
        String period,
        LocalDateTime start,
        LocalDateTime end,
        List<Metric> metrics,
        List<Breakdown> breakdowns,
        List<RankingEntry> rankings,
        List<OrderHighlight> orderHighlights,
        List<TopProduct> topProducts
) {
    public record Metric(
            String key,
            BigDecimal value,
            String unit,
            BigDecimal previousValue,
            BigDecimal deltaPercent
    ) {
    }

    public record Breakdown(String group, String key, long count) {
    }

    public record RankingEntry(
            int rank,
            Long pharmacistId,
            String firstName,
            String lastName,
            long count
    ) {
    }

    public record OrderHighlight(
            Long orderId,
            String status,
            BigDecimal totalPrice,
            LocalDateTime date
    ) {
    }

    public record TopProduct(
            Long productId,
            String productName,
            long quantity,
            long orderCount,
            BigDecimal revenue
    ) {
    }
}
