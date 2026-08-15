package com.example.dawanow.service.ai.chat;

import com.example.dawanow.dtos.response.ChatAnalyticsResponse;
import com.example.dawanow.dtos.response.ChatAnalyticsResponse.Breakdown;
import com.example.dawanow.dtos.response.ChatAnalyticsResponse.Metric;
import com.example.dawanow.dtos.response.ChatAnalyticsResponse.OrderHighlight;
import com.example.dawanow.dtos.response.ChatAnalyticsResponse.RankingEntry;
import com.example.dawanow.dtos.response.ChatAnalyticsResponse.TopProduct;
import com.example.dawanow.entity.Pharmacist;
import com.example.dawanow.entity.Pharmacy;
import com.example.dawanow.entity.User;
import com.example.dawanow.repo.AiPharmacyAnalyticsRepository;
import com.example.dawanow.repo.AiPharmacyAnalyticsRepository.PharmacistCountProjection;
import com.example.dawanow.repo.AiPharmacyAnalyticsRepository.OfferStatusCountProjection;
import com.example.dawanow.repo.AiPharmacyAnalyticsRepository.OrderStatusCountProjection;
import com.example.dawanow.service.ai.chat.AiChatModelClient.AnalyticsSpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Authorization, validation and deterministic execution boundary for chat analytics.
 * The model can only fill an {@link AnalyticsSpec}; it never receives database results.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PharmacyAnalyticsService {

    private static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");
    private static final LocalDateTime ALL_TIME_START = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final int MAX_RANKINGS = 10;
    private static final int MAX_ORDER_HIGHLIGHTS = 5;
    private static final int MAX_PRODUCTS = 10;

    private final AiPharmacyAnalyticsRepository repository;

    public AnalyticsResult analyze(User user, String preset, AnalyticsSpec modelSpec) {
        Pharmacist caller = currentPharmacist(user);
        if (caller == null || caller.getPharmacy() == null) {
            return AnalyticsResult.denied();
        }
        Pharmacy pharmacy = caller.getPharmacy();
        boolean isAdmin = pharmacy.getAdminPharmacist() != null
                && caller.getId().equals(pharmacy.getAdminPharmacist().getId());

        NormalizedRequest request;
        try {
            request = normalize(preset, modelSpec, isAdmin);
        } catch (SecurityException exception) {
            return AnalyticsResult.denied();
        } catch (IllegalArgumentException exception) {
            return AnalyticsResult.clarify(exception.getMessage());
        }

        Long pharmacistId = null;
        String scope = request.scope();
        if (!isAdmin) {
            if (!"SELF".equals(scope)) {
                return AnalyticsResult.denied();
            }
            pharmacistId = caller.getId();
        } else if ("SELF".equals(scope)) {
            pharmacistId = caller.getId();
        } else if ("EMPLOYEE".equals(scope)) {
            List<Pharmacist> matches = matchingPharmacists(
                    repository.findCurrentPharmacists(pharmacy.getId()), request.employeeName());
            if (matches.size() != 1) {
                return AnalyticsResult.clarify(matches.isEmpty()
                        ? "EMPLOYEE_NOT_FOUND"
                        : "EMPLOYEE_NAME_AMBIGUOUS");
            }
            pharmacistId = matches.getFirst().getId();
        }

        try {
            DateRange range = resolveRange(request);
            ChatAnalyticsResponse analytics = buildAnalytics(
                    pharmacy, caller, pharmacistId, request, range);
            if ("PREVIOUS_PERIOD".equals(request.comparison())) {
                if ("ALL_TIME".equals(range.period())) {
                    throw new IllegalArgumentException("COMPARISON_REQUIRES_BOUNDED_PERIOD");
                }
                Duration duration = Duration.between(range.start(), range.end());
                DateRange previousRange = new DateRange(
                        range.start().minus(duration), range.start(), "PREVIOUS_PERIOD");
                ChatAnalyticsResponse previous = buildAnalytics(
                        pharmacy, caller, pharmacistId, request, previousRange);
                analytics = withComparison(analytics, previous);
            }
            return AnalyticsResult.allowed(pharmacy.getId(), scope, analytics);
        } catch (IllegalArgumentException exception) {
            return AnalyticsResult.clarify(exception.getMessage());
        }
    }

    public boolean canViewSnapshot(User user, Long pharmacyId, String scope) {
        if (pharmacyId == null || !StringUtils.hasText(scope)) {
            return false;
        }
        Pharmacist caller = currentPharmacist(user);
        if (caller == null || caller.getPharmacy() == null
                || !pharmacyId.equals(caller.getPharmacy().getId())) {
            return false;
        }
        if ("SELF".equals(scope)) {
            return true;
        }
        return caller.getPharmacy().getAdminPharmacist() != null
                && caller.getId().equals(caller.getPharmacy().getAdminPharmacist().getId());
    }

    private Pharmacist currentPharmacist(User user) {
        if (!(user instanceof Pharmacist) || user.getId() == null) {
            return null;
        }
        return repository.findById(user.getId()).orElse(null);
    }

    private NormalizedRequest normalize(String preset, AnalyticsSpec spec, boolean isAdmin) {
        if (StringUtils.hasText(preset)) {
            return preset(preset.trim().toUpperCase(Locale.ROOT), isAdmin);
        }
        if (spec == null) {
            throw new IllegalArgumentException("MISSING_ANALYTICS_QUERY");
        }
        String metric = enumValue(spec.metric(), "OVERVIEW");
        String scope = enumValue(spec.scope(), isAdmin ? "PHARMACY" : "SELF");
        String period = enumValue(spec.period(), "THIS_MONTH");
        String direction = enumValue(spec.direction(), "TOP");
        if (StringUtils.hasText(spec.employeeName())) {
            scope = "EMPLOYEE";
        }
        if ("TOP_EMPLOYEE".equals(metric)) {
            scope = "TEAM";
        }
        if ("TOP_PRODUCTS".equals(metric)) {
            scope = "PRODUCT";
        }
        return new NormalizedRequest(metric, scope, period, direction,
                clean(spec.startDate()), clean(spec.endDate()), clean(spec.employeeName()),
                clean(spec.productName()), enumValue(spec.comparison(), "NONE"));
    }

    private NormalizedRequest preset(String preset, boolean isAdmin) {
        return switch (preset) {
            case "PHARMACY_MONTH_OVERVIEW" -> requireAdminPreset(isAdmin,
                    new NormalizedRequest("OVERVIEW", "PHARMACY", "THIS_MONTH", "TOP",
                            null, null, null, null, "NONE"));
            case "PHARMACY_MONTH_ACCEPTANCE" -> requireAdminPreset(isAdmin,
                    new NormalizedRequest("OFFER_ACCEPTANCE_RATE", "PHARMACY", "THIS_MONTH", "TOP",
                            null, null, null, null, "NONE"));
            case "PHARMACY_MONTH_TOP_EMPLOYEE" -> requireAdminPreset(isAdmin,
                    new NormalizedRequest("TOP_EMPLOYEE", "TEAM", "THIS_MONTH", "TOP",
                            null, null, null, null, "NONE"));
            case "PHARMACY_MONTH_LARGEST_ORDER" -> requireAdminPreset(isAdmin,
                    new NormalizedRequest("LARGEST_ORDER", "PHARMACY", "THIS_MONTH", "TOP",
                            null, null, null, null, "NONE"));
            case "SELF_MONTH_OVERVIEW" -> new NormalizedRequest(
                    "OVERVIEW", "SELF", "THIS_MONTH", "TOP", null, null, null, null, "NONE");
            case "SELF_MONTH_ORDERS" -> new NormalizedRequest(
                    "ORDERS_GENERATED", "SELF", "THIS_MONTH", "TOP", null, null, null, null, "NONE");
            default -> throw new IllegalArgumentException("UNKNOWN_ANALYTICS_PRESET");
        };
    }

    private NormalizedRequest requireAdminPreset(boolean isAdmin, NormalizedRequest request) {
        if (!isAdmin) {
            throw new SecurityException("Admin preset used by a regular pharmacist");
        }
        return request;
    }

    private DateRange resolveRange(NormalizedRequest request) {
        LocalDate today = LocalDate.now(CAIRO);
        return switch (request.period()) {
            case "TODAY" -> day(today, "TODAY");
            case "YESTERDAY" -> day(today.minusDays(1), "YESTERDAY");
            case "THIS_WEEK" -> {
                LocalDate start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield range(start, today.plusDays(1), "THIS_WEEK");
            }
            case "LAST_WEEK" -> {
                LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield range(thisMonday.minusWeeks(1), thisMonday, "LAST_WEEK");
            }
            case "THIS_MONTH" -> range(today.withDayOfMonth(1), today.plusDays(1), "THIS_MONTH");
            case "LAST_MONTH" -> {
                YearMonth previous = YearMonth.from(today).minusMonths(1);
                yield range(previous.atDay(1), previous.plusMonths(1).atDay(1), "LAST_MONTH");
            }
            case "THIS_YEAR" -> range(today.withDayOfYear(1), today.plusDays(1), "THIS_YEAR");
            case "LAST_YEAR" -> range(
                    LocalDate.of(today.getYear() - 1, 1, 1),
                    LocalDate.of(today.getYear(), 1, 1), "LAST_YEAR");
            case "EXACT_DATE" -> day(parseDate(request.startDate(), "DATE_REQUIRED"), "EXACT_DATE");
            case "CUSTOM_RANGE" -> {
                LocalDate start = parseDate(request.startDate(), "RANGE_START_REQUIRED");
                LocalDate end = parseDate(request.endDate(), "RANGE_END_REQUIRED");
                if (end.isBefore(start)) {
                    throw new IllegalArgumentException("INVALID_DATE_RANGE");
                }
                yield range(start, end.plusDays(1), "CUSTOM_RANGE");
            }
            case "ALL_TIME" -> new DateRange(ALL_TIME_START, today.plusDays(1).atStartOfDay(), "ALL_TIME");
            default -> throw new IllegalArgumentException("UNSUPPORTED_PERIOD");
        };
    }

    private ChatAnalyticsResponse buildAnalytics(
            Pharmacy pharmacy,
            Pharmacist caller,
            Long pharmacistId,
            NormalizedRequest request,
            DateRange range
    ) {
        Long pharmacyId = pharmacy.getId();
        List<OfferStatusCountProjection> offerCounts = repository.countOffersByStatus(
                pharmacyId, pharmacistId, range.start(), range.end());
        List<OrderStatusCountProjection> orderCounts = repository.countOrdersByStatus(
                pharmacyId, pharmacistId, range.start(), range.end());
        Map<String, Long> offers = toOfferCountMap(offerCounts);
        Map<String, Long> orders = toOrderCountMap(orderCounts);
        long offerTotal = total(offers);
        long orderTotal = total(orders);
        long acceptedOffers = offers.getOrDefault("ACCEPTED", 0L)
                + offers.getOrDefault("PARTIALLY_ACCEPTED", 0L);
        BigDecimal totalOrderValue = zero(repository.sumOrderValue(
                pharmacyId, pharmacistId, range.start(), range.end()));
        BigDecimal deliveredRevenue = zero(repository.sumDeliveredRevenue(
                pharmacyId, pharmacistId, range.start(), range.end()));
        BigDecimal averageOrderValue = zero(repository.averageOrderValue(
                pharmacyId, pharmacistId, range.start(), range.end()));

        List<Metric> metrics = new ArrayList<>();
        List<Breakdown> breakdowns = new ArrayList<>();
        List<RankingEntry> rankings = List.of();
        List<OrderHighlight> highlights = List.of();
        List<TopProduct> products = List.of();

        switch (request.metric()) {
            case "REQUESTS_RECEIVED" -> addRequestMetrics(metrics, pharmacyId, range);
            case "OFFER_ACCEPTANCE_RATE" -> addOfferMetrics(metrics, offerTotal, acceptedOffers);
            case "OFFERS_CREATED" -> metrics.add(countMetric("OFFERS_CREATED", offerTotal));
            case "ORDERS_GENERATED" -> metrics.add(countMetric("ORDERS_GENERATED", orderTotal));
            case "DELIVERED_ORDERS" -> metrics.add(countMetric(
                    "DELIVERED_ORDERS", orders.getOrDefault("DELIVERED", 0L)));
            case "CANCELLED_ORDERS" -> metrics.add(countMetric(
                    "CANCELLED_ORDERS", orders.getOrDefault("CANCELLED", 0L)));
            case "TOTAL_ORDER_VALUE" -> metrics.add(moneyMetric("TOTAL_ORDER_VALUE", totalOrderValue));
            case "DELIVERED_REVENUE" -> metrics.add(moneyMetric("DELIVERED_REVENUE", deliveredRevenue));
            case "AVERAGE_ORDER_VALUE" -> metrics.add(moneyMetric("AVERAGE_ORDER_VALUE", averageOrderValue));
            case "LARGEST_ORDER" -> highlights = orderHighlights(
                    pharmacyId, pharmacistId, range, 1);
            case "TOP_EMPLOYEE" -> rankings = teamRankings(
                    pharmacy, caller, request.direction(), range);
            case "TOP_PRODUCTS" -> products = topProducts(
                    pharmacyId, pharmacistId, range, request.productName());
            case "EMPLOYEE_PERFORMANCE", "OVERVIEW" -> {
                if (pharmacistId == null) {
                    addRequestMetrics(metrics, pharmacyId, range);
                }
                addOfferMetrics(metrics, offerTotal, acceptedOffers);
                metrics.add(countMetric("ORDERS_GENERATED", orderTotal));
                metrics.add(countMetric("DELIVERED_ORDERS", orders.getOrDefault("DELIVERED", 0L)));
                metrics.add(moneyMetric("TOTAL_ORDER_VALUE", totalOrderValue));
                metrics.add(moneyMetric("DELIVERED_REVENUE", deliveredRevenue));
                metrics.add(moneyMetric("AVERAGE_ORDER_VALUE", averageOrderValue));
                breakdowns.addAll(breakdowns("OFFERS", offers));
                breakdowns.addAll(breakdowns("ORDERS", orders));
                highlights = orderHighlights(pharmacyId, pharmacistId, range, 1);
                if (pharmacistId == null) {
                    rankings = teamRankings(pharmacy, caller, "TOP", range);
                    products = topProducts(pharmacyId, null, range, null);
                }
            }
            default -> throw new IllegalArgumentException("UNSUPPORTED_ANALYTICS_METRIC");
        }

        if (breakdowns.isEmpty()) {
            if (request.metric().startsWith("OFFER")) breakdowns.addAll(breakdowns("OFFERS", offers));
            if (request.metric().contains("ORDER")) breakdowns.addAll(breakdowns("ORDERS", orders));
        }
        return new ChatAnalyticsResponse(
                1, request.scope(), range.period(), range.start(), range.end(),
                List.copyOf(metrics), List.copyOf(breakdowns), rankings, highlights, products);
    }

    private void addRequestMetrics(List<Metric> metrics, Long pharmacyId, DateRange range) {
        long requests = repository.countRequests(pharmacyId, range.start(), range.end());
        long covered = repository.countCoveredRequests(pharmacyId, range.start(), range.end());
        metrics.add(countMetric("REQUESTS_RECEIVED", requests));
        metrics.add(countMetric("REQUESTS_COVERED", covered));
        metrics.add(percentMetric("REQUEST_COVERAGE_RATE", ratio(covered, requests)));
    }

    private void addOfferMetrics(List<Metric> metrics, long offers, long accepted) {
        metrics.add(countMetric("OFFERS_CREATED", offers));
        metrics.add(countMetric("ACCEPTED_OFFERS", accepted));
        metrics.add(percentMetric("OFFER_ACCEPTANCE_RATE", ratio(accepted, offers)));
    }

    private List<RankingEntry> teamRankings(
            Pharmacy pharmacy,
            Pharmacist caller,
            String direction,
            DateRange range
    ) {
        List<Pharmacist> staff = repository.findCurrentPharmacists(pharmacy.getId()).stream()
                .filter(member -> !member.getId().equals(caller.getId()))
                .toList();
        Map<Long, Long> counts = new HashMap<>();
        for (PharmacistCountProjection projection : repository.countGeneratedOrdersByRegularPharmacist(
                pharmacy.getId(), caller.getId(), range.start(), range.end())) {
            counts.put(projection.getPharmacistId(), safeLong(projection.getActivityCount()));
        }
        Comparator<Pharmacist> comparator = Comparator.comparingLong(
                member -> counts.getOrDefault(member.getId(), 0L));
        if (!"BOTTOM".equals(direction)) comparator = comparator.reversed();
        comparator = comparator.thenComparingLong(Pharmacist::getId);
        List<Pharmacist> ordered = staff.stream().sorted(comparator).limit(MAX_RANKINGS).toList();
        List<RankingEntry> result = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            Pharmacist member = ordered.get(index);
            result.add(new RankingEntry(index + 1, member.getId(), member.getFirstName(),
                    member.getLastName(), counts.getOrDefault(member.getId(), 0L)));
        }
        return List.copyOf(result);
    }

    private List<OrderHighlight> orderHighlights(
            Long pharmacyId, Long pharmacistId, DateRange range, int limit) {
        return repository.findLargestOrders(pharmacyId, pharmacistId, range.start(), range.end(),
                        PageRequest.of(0, Math.min(limit, MAX_ORDER_HIGHLIGHTS)))
                .stream()
                .map(order -> new OrderHighlight(
                        order.getOrderId(), order.getStatus().name(), order.getTotalPrice(),
                        order.getOrderDate()))
                .toList();
    }

    private List<TopProduct> topProducts(
            Long pharmacyId, Long pharmacistId, DateRange range, String productName) {
        String query = StringUtils.hasText(productName)
                ? "%" + productName.trim().toLowerCase(Locale.ROOT) + "%"
                : null;
        return repository.findTopDeliveredProducts(
                        pharmacyId, pharmacistId, range.start(), range.end(), query,
                        PageRequest.of(0, MAX_PRODUCTS))
                .stream()
                .map(product -> new TopProduct(
                        product.getProductId(), product.getProductName(),
                        safeLong(product.getQuantity()), safeLong(product.getOrderCount()),
                        zero(product.getRevenue())))
                .toList();
    }

    private List<Pharmacist> matchingPharmacists(List<Pharmacist> pharmacists, String name) {
        if (!StringUtils.hasText(name)) {
            return List.of();
        }
        String target = normalizeName(name);
        List<Pharmacist> exact = pharmacists.stream()
                .filter(member -> normalizeName(fullName(member))
                        .equals(target))
                .toList();
        if (!exact.isEmpty()) return exact;
        return pharmacists.stream()
                .filter(member -> normalizeName(fullName(member))
                        .contains(target))
                .toList();
    }

    private ChatAnalyticsResponse withComparison(
            ChatAnalyticsResponse current,
            ChatAnalyticsResponse previous
    ) {
        Map<String, BigDecimal> previousByKey = new HashMap<>();
        previous.metrics().forEach(metric -> previousByKey.put(metric.key(), metric.value()));
        List<Metric> compared = current.metrics().stream().map(metric -> {
            BigDecimal previousValue = previousByKey.get(metric.key());
            if (previousValue == null) return metric;
            BigDecimal delta = null;
            if (previousValue.compareTo(BigDecimal.ZERO) == 0) {
                if (metric.value().compareTo(BigDecimal.ZERO) == 0) {
                    delta = BigDecimal.ZERO.setScale(1);
                }
            } else {
                delta = metric.value().subtract(previousValue)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(previousValue.abs(), 1, RoundingMode.HALF_UP);
            }
            return new Metric(metric.key(), metric.value(), metric.unit(), previousValue, delta);
        }).toList();
        return new ChatAnalyticsResponse(
                current.schemaVersion(), current.scope(), current.period(), current.start(), current.end(),
                compared, current.breakdowns(), current.rankings(), current.orderHighlights(),
                current.topProducts());
    }

    private Map<String, Long> toOfferCountMap(List<OfferStatusCountProjection> projections) {
        Map<String, Long> counts = new HashMap<>();
        for (OfferStatusCountProjection projection : projections) {
            counts.put(projection.getStatus().name(), safeLong(projection.getActivityCount()));
        }
        return counts;
    }

    private Map<String, Long> toOrderCountMap(List<OrderStatusCountProjection> projections) {
        Map<String, Long> counts = new HashMap<>();
        for (OrderStatusCountProjection projection : projections) {
            counts.put(projection.getStatus().name(), safeLong(projection.getActivityCount()));
        }
        return counts;
    }

    private List<Breakdown> breakdowns(String group, Map<String, Long> counts) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new Breakdown(group, entry.getKey(), entry.getValue()))
                .toList();
    }

    private long total(Map<String, Long> counts) {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    private Metric countMetric(String key, long value) {
        return new Metric(key, BigDecimal.valueOf(value), "COUNT", null, null);
    }

    private Metric moneyMetric(String key, BigDecimal value) {
        return new Metric(key, value.setScale(2, RoundingMode.HALF_UP), "EGP", null, null);
    }

    private Metric percentMetric(String key, BigDecimal value) {
        return new Metric(key, value, "PERCENT", null, null);
    }

    private BigDecimal ratio(long numerator, long denominator) {
        if (denominator == 0) return BigDecimal.ZERO.setScale(1);
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private String enumValue(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : fallback;
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String fullName(Pharmacist pharmacist) {
        return (pharmacist.getFirstName() == null ? "" : pharmacist.getFirstName()) + " "
                + (pharmacist.getLastName() == null ? "" : pharmacist.getLastName());
    }

    private LocalDate parseDate(String value, String error) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException(error);
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(error);
        }
    }

    private DateRange day(LocalDate date, String period) {
        return range(date, date.plusDays(1), period);
    }

    private DateRange range(LocalDate start, LocalDate endExclusive, String period) {
        return new DateRange(start.atStartOfDay(), endExclusive.atTime(LocalTime.MIN), period);
    }

    private record NormalizedRequest(
            String metric,
            String scope,
            String period,
            String direction,
            String startDate,
            String endDate,
            String employeeName,
            String productName,
            String comparison
    ) {
    }

    private record DateRange(LocalDateTime start, LocalDateTime end, String period) {
    }

    public record AnalyticsResult(
            Status status,
            String clarification,
            Long pharmacyId,
            String scope,
            ChatAnalyticsResponse analytics
    ) {
        public enum Status { ALLOWED, DENIED, CLARIFICATION }

        private static AnalyticsResult allowed(
                Long pharmacyId, String scope, ChatAnalyticsResponse analytics) {
            return new AnalyticsResult(Status.ALLOWED, null, pharmacyId, scope, analytics);
        }

        private static AnalyticsResult denied() {
            return new AnalyticsResult(Status.DENIED, null, null, null, null);
        }

        private static AnalyticsResult clarify(String reason) {
            return new AnalyticsResult(Status.CLARIFICATION, reason, null, null, null);
        }
    }
}
