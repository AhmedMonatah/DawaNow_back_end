package com.example.dawanow.service.ai;

import com.example.dawanow.config.AiChatProperties;
import com.example.dawanow.dtos.response.AiDashboardSummaryResponse;
import com.example.dawanow.dtos.response.MostSoldProductResponse;
import com.example.dawanow.dtos.response.PharmacyDashboardResponse;
import com.example.dawanow.entity.DashboardPeriod;
import com.example.dawanow.entity.User;
import com.example.dawanow.service.CurrentUserProvider;
import com.example.dawanow.service.PharmacyDashboardService;
import com.example.dawanow.service.ai.chat.AiChatModelClient;
import com.example.dawanow.service.ai.chat.AiChatModelClient.GatewayMessage;
import com.example.dawanow.service.ai.chat.AiChatPromptFactory;
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

        String summary = generate(dashboard, period, language);
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

    private void evictExpired() {
        Instant cutoff = Instant.now().minus(properties.getDashboardSummaryTtl());
        cache.entrySet().removeIf(entry -> entry.getValue().createdAt().isBefore(cutoff));
    }
}
