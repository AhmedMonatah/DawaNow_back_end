package com.example.dawanow.repo;

import com.example.dawanow.entity.MasterOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface MasterOrderRepository extends JpaRepository<MasterOrder, Long> {
    Optional<MasterOrder> findByOrderId(Long orderId);
    Optional<MasterOrder> findByPaymentIntentId(String paymentIntentId);
}
