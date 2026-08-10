package com.example.dawanow.service;

import com.example.dawanow.dtos.response.*;
import com.example.dawanow.entity.*;
import com.example.dawanow.exception.ResourceNotFoundException;
import com.example.dawanow.mapper.MasterOrderMapper;
import com.example.dawanow.repo.MasterOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

        List<OrderDraftResponse> orderDraftResponses = orders.stream()
                .sorted(Comparator.comparing(
                        order -> order.getPharmacy().getId()
                ))
                .map(this::toOrderDraftResponse)
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

                    List<OrderDraftResponse> orderDraftResponses = orders.stream()
                            .sorted(Comparator.comparing(order -> order.getPharmacy().getId()))
                            .map(this::toOrderDraftResponse)
                            .toList();

                    return masterOrderMapper.toResponse(
                            masterOrder,
                            orderDraftResponses
                    );
                });

        return PaginatedResponse.from(responsePage);
    }




    private OrderDraftResponse toOrderDraftResponse(Order order) {

        Pharmacy pharmacy = order.getPharmacy();

        List<OfferedItemResponse> items = order.getItems().stream()
                .map(this::toOfferedItemResponse)
                .toList();

        return new OrderDraftResponse(
                order.getId(),
                pharmacy.getId(),
                pharmacy.getName(),
                pharmacy.getLatitude(),
                pharmacy.getLongitude(),
                items
        );
    }
    private OfferedItemResponse toOfferedItemResponse(OrderItem item) {
        return new OfferedItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getProduct().getName()
        );
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


    private Customer requireCurrentCustomer() {
        User currentUser = currentUserProvider.get();
        if (!(currentUser instanceof Customer customer)) {
            throw new AccessDeniedException("Only customers can view their orders");
        }
        return customer;
    }

}
