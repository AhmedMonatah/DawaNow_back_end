package com.example.dawanow.service;

import com.example.dawanow.dtos.response.*;
import com.example.dawanow.entity.*;
import com.example.dawanow.exception.ResourceNotFoundException;
import com.example.dawanow.mapper.MasterOrderMapper;
import com.example.dawanow.mapper.OrderMapper;
import com.example.dawanow.repo.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private static final String DEFAULT_LANG = "en";
    private static final String ARABIC = "ar";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final PharmacyRepository pharmacyRepository;
    private final CurrentUserProvider currentUserProvider;
    private final OrderMapper orderMapper;
    private final MasterOrderRepository masterOrderRepository;
    private final MasterOrderMapper masterOrderMapper;


    @Transactional(readOnly = true)
    public PaginatedResponse<OrderResponse> getPharmacyOrders(Long pharmacyId, String lang, Pageable pageable) {
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy not found"));
        User currentUser = currentUserProvider.get();
        String language = normalizeLanguage(lang);

        if (!isApplicationAdmin(currentUser) && !isPharmacistAtPharmacy(currentUser, pharmacy)) {
            throw new AccessDeniedException(
                    "Only the pharmacy's pharmacist or a system administrator can view these orders"
            );
        }

        Page<Order> orders = orderRepository.findByPharmacyId(pharmacyId, pageable);
        Map<Long, List<OrderItemResponse>> itemsByOrderId = resolveItems(orders.getContent(), language);

        return PaginatedResponse.from(orders.map(order -> toResponse(order, itemsByOrderId)));
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<OrderResponse> getAllOrders(String lang, Pageable pageable) {
        User currentUser = currentUserProvider.get();
        if (!isApplicationAdmin(currentUser)) {
            throw new AccessDeniedException("Only system administrators can view all orders");
        }
        String language = normalizeLanguage(lang);

        Page<Order> orders = orderRepository.findAll(pageable);
        Map<Long, List<OrderItemResponse>> itemsByOrderId = resolveItems(orders.getContent(), language);

        return PaginatedResponse.from(orders.map(order -> toResponse(order, itemsByOrderId)));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id, String lang) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        User currentUser = currentUserProvider.get();
        String language = normalizeLanguage(lang);

        log.info("Current user: {} with role {}", currentUser.getId(), currentUser.getRole());
        log.info("Order user: {} with role {}", order.getUser().getId(), order.getUser().getRole());

        boolean ownsOrder = currentUser.getRole() == UserRole.CUSTOMER
                && order.getUser().getId().equals(currentUser.getId());
        log.info("{} == {} ? {}", order.getUser().getId(), currentUser.getId(), ownsOrder);
        boolean pharmacistCanView = isPharmacistAtPharmacy(currentUser, order.getPharmacy());
        if (!isApplicationAdmin(currentUser) && !ownsOrder && !pharmacistCanView) {
            log.warn("Access denied for user {} to view order {}", currentUser.getId(), order.getId());
            throw new AccessDeniedException("You are not allowed to view this order");
        }

        Map<Long, List<OrderItemResponse>> itemsByOrderId = resolveItems(List.of(order), language);
        return toResponse(order, itemsByOrderId);
    }


    private OrderResponse toResponse(Order order, Map<Long, List<OrderItemResponse>> itemsByOrderId) {
        return orderMapper.toResponse(order, itemsByOrderId.getOrDefault(order.getId(), List.of()));
    }

    /**
     * Raw line data is fetched for all orders in one query (product left null),
     * then the distinct products are resolved in a single localized query and
     * filled in place. Results are grouped by order id.
     */
    private Map<Long, List<OrderItemResponse>> resolveItems(List<Order> orders, String lang) {
        List<Long> orderIds = orders.stream()
                .map(Order::getId)
                .toList();
        if (orderIds.isEmpty()) {
            return Map.of();
        }

        List<OrderItemResponse> items = orderItemRepository.findByOrderIdIn(orderIds);

        List<Long> productIds = items.stream()
                .map(OrderItemResponse::getProductId)
                .filter(id -> id != null)   // deleted products have null productId
                .distinct()
                .toList();

        if (!productIds.isEmpty()) {
            Map<Long, ProductSummaryResponse> productsById = productRepository
                    .findAllLocalized(productIds, lang, DEFAULT_LANG)
                    .stream()
                    .collect(Collectors.toMap(ProductSummaryResponse::id, Function.identity()));

            items.forEach(item -> item.setProduct(productsById.get(item.getProductId())));
        }

        return items.stream().collect(Collectors.groupingBy(
                OrderItemResponse::getOrderId,
                LinkedHashMap::new,
                Collectors.toList()
        ));
    }

    private boolean isApplicationAdmin(User user) {
        return user.getRole() == UserRole.ADMIN;
    }

    private boolean isPharmacistAtPharmacy(User user, Pharmacy pharmacy) {
        if (!(user instanceof Pharmacist pharmacist) || pharmacist.getPharmacy() == null) {
            return false;
        }

        return pharmacy.getId().equals(pharmacist.getPharmacy().getId());
    }

    private Customer requireCurrentCustomer() {
        User currentUser = currentUserProvider.get();
        if (!(currentUser instanceof Customer customer)) {
            throw new AccessDeniedException("Only customers can view their orders");
        }
        return customer;
    }

    private String normalizeLanguage(String lang) {
        String language = StringUtils.hasText(lang)
                ? lang.trim().toLowerCase(Locale.ROOT)
                : DEFAULT_LANG;
        if (!DEFAULT_LANG.equals(language) && !ARABIC.equals(language)) {
            throw new IllegalArgumentException("Unsupported language. Supported values are en and ar");
        }
        return language;
    }


}
