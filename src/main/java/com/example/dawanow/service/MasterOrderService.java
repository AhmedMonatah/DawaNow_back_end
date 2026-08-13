package com.example.dawanow.service;

import com.example.dawanow.dtos.response.MasterOrderResponse;
import com.example.dawanow.dtos.response.OrderDraftResponse;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.entity.Customer;
import com.example.dawanow.entity.MasterOrder;
import com.example.dawanow.entity.OrderStatus;
import com.example.dawanow.entity.User;
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

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MasterOrderService {

    private final MasterOrderRepository masterOrderRepository;
    private final CurrentUserProvider currentUserProvider;
    private final MasterOrderMapper masterOrderMapper;
    private final OrderService orderService;


    @Transactional(readOnly = true)
    public MasterOrderResponse getMasterOrderById(Long id, String lang) {
        MasterOrder masterOrder = masterOrderRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Master Order not found"));

        List<OrderDraftResponse> drafts =
                orderService.getOrdersByMasterId(id, lang);

        return masterOrderMapper.toResponse(masterOrder, drafts);
    }


    @Transactional(readOnly = true)
    public PaginatedResponse<MasterOrderResponse> getCurrentCustomerMasterOrders(
            String lang,
            OrderStatus status,
            Pageable pageable) {

        Customer currentCustomer = requireCurrentCustomer();

        Page<MasterOrder> masterOrderPage = status == null
                ? masterOrderRepository.findByUserId(currentCustomer.getId(), pageable)
                : masterOrderRepository.findByUserIdAndStatus(currentCustomer.getId(), status, pageable);

        List<Long> masterOrderIds = masterOrderPage.getContent()
                .stream()
                .map(MasterOrder::getId)
                .toList();

        Map<Long, List<OrderDraftResponse>> draftsByMasterId =
                orderService.getOrdersByMasterIds(masterOrderIds, lang);

        Page<MasterOrderResponse> responsePage =
                masterOrderPage.map(masterOrder ->
                        masterOrderMapper.toResponse(
                                masterOrder,
                                draftsByMasterId.getOrDefault(masterOrder.getId(), List.of())
                        )
                );

        return PaginatedResponse.from(responsePage);
    }


    private Customer requireCurrentCustomer() {
        User currentUser = currentUserProvider.get();
        if (!(currentUser instanceof Customer customer)) {
            throw new AccessDeniedException("Only customers can view their orders");
        }
        return customer;
    }

}