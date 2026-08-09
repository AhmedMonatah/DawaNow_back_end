package com.example.dawanow.listener;

import com.example.dawanow.entity.FulfillmentMethod;
import com.example.dawanow.entity.MasterOrder;
import com.example.dawanow.entity.OrderStatus;
import com.example.dawanow.event.SubOrderStatusChangedEvent;
import com.example.dawanow.exception.ResourceNotFoundException;
import com.example.dawanow.repo.MasterOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OrderStatusEventListener {

    private final MasterOrderRepository masterOrderRepository;

    @EventListener
    @Transactional
    public void handleSubOrderStatusChanged(
            SubOrderStatusChangedEvent event) {

        MasterOrder masterOrder = masterOrderRepository
                .findById(event.masterOrderId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Master order not found"));


        boolean allReady = masterOrder.getOrders().stream()
                .allMatch(order -> order.getStatus() == OrderStatus.READY_FOR_PICKUP || order.getStatus() == OrderStatus.READY_FOR_DELIVERY);

        if (allReady) {
            masterOrder.setOrderStatus(
                    masterOrder.getFulfillmentMethod() == FulfillmentMethod.PICKUP
                            ? OrderStatus.READY_FOR_PICKUP
                            : OrderStatus.READY_FOR_DELIVERY
            );
        }
    }
}