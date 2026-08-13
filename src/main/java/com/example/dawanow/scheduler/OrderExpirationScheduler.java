package com.example.dawanow.scheduler;

import com.example.dawanow.entity.MasterOrder;
import com.example.dawanow.entity.OrderStatus;
import com.example.dawanow.entity.PaymentStatus;
import com.example.dawanow.repo.MasterOrderRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class OrderExpirationScheduler {

    private final MasterOrderRepository masterOrderRepository;

    public OrderExpirationScheduler(MasterOrderRepository masterOrderRepository) {
        this.masterOrderRepository = masterOrderRepository;
    }

    @Scheduled(fixedDelay = 900_000)
    @Transactional
    public void expirePendingPayments() {

        List<MasterOrder> expiredOrders =
                masterOrderRepository.findExpiredPaymentOrders(
                        OrderStatus.PENDING_PAYMENT,
                        LocalDateTime.now()
                );

        for (MasterOrder order : expiredOrders) {
            order.applyOrderStatus(OrderStatus.CANCELLED);
            order.setPaymentStatus(PaymentStatus.EXPIRED);
        }
    }
}