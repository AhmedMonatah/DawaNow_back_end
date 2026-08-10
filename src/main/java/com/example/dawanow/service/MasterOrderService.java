package com.example.dawanow.service;

import com.example.dawanow.dtos.response.*;
import com.example.dawanow.entity.*;
import com.example.dawanow.exception.ResourceNotFoundException;
import com.example.dawanow.mapper.MasterOrderMapper;
import com.example.dawanow.repo.MasterOrderRepository;
import com.example.dawanow.repo.OrderItemRepository;
import com.example.dawanow.repo.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MasterOrderService {


    private static final String DEFAULT_LANG = "en";
    private static final String ARABIC = "ar";

    private final MasterOrderRepository masterOrderRepository;
    private final CurrentUserProvider currentUserProvider;
    private final MasterOrderMapper masterOrderMapper;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;


    @Transactional(readOnly = true)
    public MasterOrderResponse getMasterOrderById(Long id, String lang)
            throws AccessDeniedException {

        MasterOrder masterOrder = masterOrderRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Master Order not found"));

        User currentUser = currentUserProvider.get();
        String language = normalizeLanguage(lang);

        boolean ownsOrder =
                currentUser.getRole() == UserRole.CUSTOMER
                        && masterOrder.getUser().getId().equals(currentUser.getId());

        if (!ownsOrder) {
            throw new AccessDeniedException(
                    "You are not allowed to access this master order"
            );
        }

        List<Order> orders = masterOrder.getOrders();

        List<OrderItemResponse> itemsByOrderId = resolveItems(orders, language).getOrDefault(masterOrder.getId(), List.of());


        List<OrderDraftResponse> orderDraftResponses = orders.stream()
                .sorted(Comparator.comparing(
                        order -> order.getPharmacy().getId()
                ))
                .map(order-> toOrderDraftResponse(order, itemsByOrderId))
                .toList();

        return masterOrderMapper.toResponse(
                masterOrder,
                orderDraftResponses
        );
    }



    @Transactional(readOnly = true)
    public PaginatedResponse<MasterOrderResponse> getCurrentCustomerMasterOrders(
            String lang,
            Pageable pageable) {

        Customer currentCustomer = requireCurrentCustomer();
        String language = normalizeLanguage(lang);

        Page<MasterOrder> masterOrderPage =
                masterOrderRepository.findByUserId(
                        currentCustomer.getId(),
                        pageable
                );

        Page<MasterOrderResponse> responsePage =
                masterOrderPage.map(masterOrder -> {

                    List<Order> orders = masterOrder.getOrders();

                    List<OrderItemResponse> itemsByOrderId = resolveItems(orders, language).getOrDefault(masterOrder.getId(), List.of());

                    List<OrderDraftResponse> orderDraftResponses = orders.stream()
                            .sorted(Comparator.comparing(order -> order.getPharmacy().getId()))
                            .map(order-> toOrderDraftResponse(order, itemsByOrderId))
                            .toList();

                    return masterOrderMapper.toResponse(
                            masterOrder,
                            orderDraftResponses
                    );
                });

        return PaginatedResponse.from(responsePage);
    }




    private OrderDraftResponse toOrderDraftResponse(Order order, List<OrderItemResponse> items) {

        Pharmacy pharmacy = order.getPharmacy();

        return new OrderDraftResponse(
                order.getId(),
                pharmacy.getId(),
                pharmacy.getName(),
                pharmacy.getLatitude(),
                pharmacy.getLongitude(),
                items
        );
    }
//    private OfferedItemResponse toOfferedItemResponse(OrderItem item) {
//        return new OfferedItemResponse(
//                item.getId(),
//                item.getProduct().getId(),
//                item.getProduct().getName()
//        );
//    }


    private String normalizeLanguage(String lang) {
        String language = StringUtils.hasText(lang)
                ? lang.trim().toLowerCase(Locale.ROOT)
                : DEFAULT_LANG;
        if (!DEFAULT_LANG.equals(language) && !ARABIC.equals(language)) {
            throw new IllegalArgumentException("Unsupported language. Supported values are en and ar");
        }
        return language;
    }


    private Customer requireCurrentCustomer() {
        User currentUser = currentUserProvider.get();
        if (!(currentUser instanceof Customer customer)) {
            throw new AccessDeniedException("Only customers can view their orders");
        }
        return customer;
    }


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

}
