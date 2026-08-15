package com.example.dawanow.service.ai;

import com.example.dawanow.config.AiChatProperties;
import com.example.dawanow.dtos.response.AiDashboardSummaryResponse;
import com.example.dawanow.dtos.response.MostSoldProductResponse;
import com.example.dawanow.dtos.response.PharmacistPerformanceEntryResponse;
import com.example.dawanow.dtos.response.PharmacistRankingResponse;
import com.example.dawanow.dtos.response.PharmacyDashboardResponse;
import com.example.dawanow.entity.ChatPerformanceDirection;
import com.example.dawanow.entity.ChatPerformanceMetric;
import com.example.dawanow.entity.DashboardPeriod;
import com.example.dawanow.entity.OfferStatus;
import com.example.dawanow.entity.User;
import com.example.dawanow.repo.OrderRepository;
import com.example.dawanow.repo.PharmacyOfferRepository;
import com.example.dawanow.service.CurrentUserProvider;
import com.example.dawanow.service.PharmacyDashboardService;
import com.example.dawanow.service.ai.chat.AiChatModelClient;
import com.example.dawanow.service.ai.chat.AiChatModelClient.GatewayMessage;
import com.example.dawanow.service.ai.chat.AiChatPromptFactory;
import com.example.dawanow.service.ai.chat.PharmacistPerformanceService;
import com.example.dawanow.service.ai.chat.PharmacistPerformanceService.PerformanceResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Natural-language summary of the pharmacy dashboard.
 *
 * <p>Owner validation comes for free: the underlying
 * {@link PharmacyDashboardService#getDashboard} throws
 * {@code AccessDeniedException} unless the caller is the pharmacy's admin
 * pharmacist. Summaries are cached per user/period/language because the
 * metrics barely move minute-to-minute and gateway calls are slow; a model
 * failure falls back to a deterministic numeric summary instead of erroring
 * a nice-to-have endpoint.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiDashboardSummaryService {

    private static final int SUMMARY_MAX_TOKENS = 400;

    private final PharmacyDashboardService dashboardService;
    private final AiChatModelClient modelClient;
    private final AiChatPromptFactory promptFactory;
    private final CurrentUserProvider currentUserProvider;
    private final AiChatProperties properties;
    private final PharmacistPerformanceService pharmacistPerformanceService;
    private final OrderRepository orderRepository;
    private final PharmacyOfferRepository pharmacyOfferRepository;

    private final Map<String, CachedSummary> cache = new ConcurrentHashMap<>();

    private record CachedSummary(String summary, Instant createdAt) {
    }

    public AiDashboardSummaryResponse getSummary(DashboardPeriod period, String language) {
        User user = currentUserProvider.get();
        // Admin-only check happens inside getDashboard — before any cache read
        // could leak a previously generated summary to a non-admin.
        PharmacyDashboardResponse dashboard = dashboardService.getDashboard(period);

        String key = user.getId() + "|" + period + "|" + language;
        evictExpired();
        CachedSummary cached = cache.get(key);
        if (cached != null) {
            return new AiDashboardSummaryResponse(
                    period.name(), language, cached.summary(), cached.createdAt(), true);
        }

        DashboardInsightMetrics insights = loadInsightMetrics(user, dashboard, period);
        String summary = generate(dashboard, period, language)
                + operationalInsights(dashboard, insights, language);
        Instant now = Instant.now();
        cache.put(key, new CachedSummary(summary, now));
        return new AiDashboardSummaryResponse(period.name(), language, summary, now, false);
    }

    private String generate(PharmacyDashboardResponse dashboard, DashboardPeriod period, String language) {
        try {
            String summary = modelClient.generateText(
                    promptFactory.dashboardSummarySystemPrompt(language),
                    List.of(new GatewayMessage("user", metricsBlock(dashboard, period))),
                    SUMMARY_MAX_TOKENS
            );
            if (StringUtils.hasText(summary)) {
                return summary.trim();
            }
        } catch (RuntimeException exception) {
            log.warn("Dashboard summary generation failed, using numeric fallback: {}",
                    exception.getMessage());
        }
        return fallbackSummary(dashboard, language);
    }

    /** Compact plain lines: cheap tokens and unambiguous for a small model. */
    private String metricsBlock(PharmacyDashboardResponse dashboard, DashboardPeriod period) {
        String topSelling = dashboard.topSellingProducts() == null
                ? ""
                : dashboard.topSellingProducts().stream()
                        .map(product -> product.productName() + " x" + product.totalQuantitySold()
                                + " (EGP " + product.totalRevenue() + ")")
                        .collect(Collectors.joining("; "));
        int recentOrders = dashboard.recentOrders() == null ? 0 : dashboard.recentOrders().size();
        return "period: " + period.name() + "\n"
                + "totalRevenueEGP: " + dashboard.totalRevenue() + "\n"
                + "totalOrders: " + dashboard.totalOrders() + "\n"
                + "requestsReceived: " + dashboard.requestsReceived() + "\n"
                + "offersCreated: " + dashboard.offersCreated() + "\n"
                + "topSellingProducts: " + (topSelling.isEmpty() ? "none" : topSelling) + "\n"
                + "recentOrdersCount: " + recentOrders;
    }

    private String fallbackSummary(PharmacyDashboardResponse dashboard, String language) {
        MostSoldProductResponse top = dashboard.topSellingProducts() == null
                || dashboard.topSellingProducts().isEmpty()
                        ? null
                        : dashboard.topSellingProducts().getFirst();
        if ("ar".equals(language)) {
            return "الإيرادات: **" + dashboard.totalRevenue() + "** جنيه من **"
                    + dashboard.totalOrders() + "** طلب. الطلبات الواردة: **"
                    + dashboard.requestsReceived() + "** والعروض المقدمة: **"
                    + dashboard.offersCreated() + "**."
                    + (top == null ? "" : " الأكثر مبيعًا: **" + top.productName() + "**.");
        }
        return "Revenue: **EGP " + dashboard.totalRevenue() + "** from **"
                + dashboard.totalOrders() + "** orders. Requests received: **"
                + dashboard.requestsReceived() + "**, offers created: **"
                + dashboard.offersCreated() + "**."
                + (top == null ? "" : " Best seller: **" + top.productName() + "**.");
    }

    /**
     * Calculates sensitive pharmacy insights locally. These values are returned
     * only to the authenticated admin and are never included in the model prompt.
     */
    private DashboardInsightMetrics loadInsightMetrics(
            User user,
            PharmacyDashboardResponse dashboard,
            DashboardPeriod period
    ) {
        PerformanceResult performance = pharmacistPerformanceService.rank(
                user,
                ChatPerformanceMetric.BOTH,
                period,
                ChatPerformanceDirection.TOP
        );
        Long pharmacyId = performance.pharmacyId();
        if (pharmacyId == null) {
            return DashboardInsightMetrics.empty();
        }

        var start = period.getStartDateTime();
        var end = period.getEndDateTime();
        BigDecimal biggestOrder = orderRepository.findMaximumTotalPriceByPharmacyIdAndDateBetween(
                pharmacyId, start, end);
        long acceptedOffers = pharmacyOfferRepository.countByPharmacyIdAndStatusInAndCreatedAtBetween(
                pharmacyId,
                List.of(OfferStatus.ACCEPTED, OfferStatus.PARTIALLY_ACCEPTED),
                start,
                end
        );
        BigDecimal averageOrder = dashboard.totalOrders() == 0
                ? BigDecimal.ZERO
                : dashboard.totalRevenue().divide(
                        BigDecimal.valueOf(dashboard.totalOrders()),
                        2,
                        RoundingMode.HALF_UP
                );

        return new DashboardInsightMetrics(
                biggestOrder == null ? BigDecimal.ZERO : biggestOrder,
                averageOrder,
                acceptedOffers,
                percentage(acceptedOffers, dashboard.offersCreated()),
                topEmployee(performance.rankings(), ChatPerformanceMetric.SUCCESSFUL_ORDERS),
                topEmployee(performance.rankings(), ChatPerformanceMetric.OFFERS_CREATED)
        );
    }

    private BigDecimal percentage(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }

    private EmployeeMetric topEmployee(
            List<PharmacistRankingResponse> rankings,
            ChatPerformanceMetric metric
    ) {
        return rankings.stream()
                .filter(ranking -> metric.name().equals(ranking.metric()))
                .map(PharmacistRankingResponse::entries)
                .filter(entries -> entries != null && !entries.isEmpty())
                .map(List::getFirst)
                .map(this::toEmployeeMetric)
                .findFirst()
                .orElse(null);
    }

    private EmployeeMetric toEmployeeMetric(PharmacistPerformanceEntryResponse entry) {
        return new EmployeeMetric(
                (entry.firstName() + " " + entry.lastName()).trim(),
                entry.count()
        );
    }

    private String operationalInsights(
            PharmacyDashboardResponse dashboard,
            DashboardInsightMetrics insights,
            String language
    ) {
        StringBuilder text = new StringBuilder();
        if ("ar".equals(language)) {
            text.append("\n- الطلبات المستلمة: **").append(dashboard.requestsReceived())
                    .append("** والعروض المنشأة: **").append(dashboard.offersCreated()).append("**.")
                    .append("\n- نسبة قبول العروض: **")
                    .append(insights.offerAcceptanceRatePercent()).append("%** (**")
                    .append(insights.acceptedOffers()).append("** عرضًا مقبولًا).")
                    .append("\n- أكبر طلب: **").append(insights.biggestOrderValue())
                    .append(" جنيه** ومتوسط قيمة الطلب: **")
                    .append(insights.averageOrderValue()).append(" جنيه**.");
            appendEmployeeInsight(text, insights.topSuccessfulOrderPharmacist(), true, true);
            appendEmployeeInsight(text, insights.topOfferCreator(), true, false);
        } else {
            text.append("\n- Requests received: **").append(dashboard.requestsReceived())
                    .append("**; offers created: **").append(dashboard.offersCreated()).append("**.")
                    .append("\n- Offer acceptance: **")
                    .append(insights.offerAcceptanceRatePercent()).append("%** (**")
                    .append(insights.acceptedOffers()).append("** accepted offers).")
                    .append("\n- Biggest order: **EGP ").append(insights.biggestOrderValue())
                    .append("**; average order value: **EGP ")
                    .append(insights.averageOrderValue()).append("**.");
            appendEmployeeInsight(text, insights.topSuccessfulOrderPharmacist(), false, true);
            appendEmployeeInsight(text, insights.topOfferCreator(), false, false);
        }
        return text.toString();
    }

    private void appendEmployeeInsight(
            StringBuilder text,
            EmployeeMetric employee,
            boolean arabic,
            boolean successfulOrders
    ) {
        if (employee == null) {
            return;
        }
        if (arabic) {
            text.append(successfulOrders
                            ? "\n- الأفضل في الطلبات الناجحة: **"
                            : "\n- الأكثر إنشاءً للعروض: **")
                    .append(employee.name()).append("** بعدد **")
                    .append(employee.count()).append("**.");
        } else {
            text.append(successfulOrders
                            ? "\n- Top employee by successful orders: **"
                            : "\n- Top offer creator: **")
                    .append(employee.name()).append("** with **")
                    .append(employee.count()).append("**.");
        }
    }

    private record EmployeeMetric(String name, long count) {
    }

    private record DashboardInsightMetrics(
            BigDecimal biggestOrderValue,
            BigDecimal averageOrderValue,
            long acceptedOffers,
            BigDecimal offerAcceptanceRatePercent,
            EmployeeMetric topSuccessfulOrderPharmacist,
            EmployeeMetric topOfferCreator
    ) {
        private static DashboardInsightMetrics empty() {
            return new DashboardInsightMetrics(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    0,
                    BigDecimal.ZERO,
                    null,
                    null
            );
        }
    }

    private void evictExpired() {
        Instant cutoff = Instant.now().minus(properties.getDashboardSummaryTtl());
        cache.entrySet().removeIf(entry -> entry.getValue().createdAt().isBefore(cutoff));
    }
}
