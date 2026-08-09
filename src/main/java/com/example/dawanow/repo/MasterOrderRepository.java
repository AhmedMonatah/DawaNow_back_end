package com.example.dawanow.repo;

import com.example.dawanow.entity.MasterOrder;
import com.example.dawanow.entity.OrderStatus;
import com.example.dawanow.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


public interface MasterOrderRepository extends JpaRepository<MasterOrder, Long> {
    Optional<MasterOrder> findById(Long orderId);
    Optional<MasterOrder> findByRequestId(Long requestId);
    Optional<MasterOrder> findByPaymentIntentId(String paymentIntentId);
    Boolean existsByRequestId(Long medicineRequestId);
    @Query("""
    SELECT o
    FROM MasterOrder o
    WHERE o.orderStatus = :status
      AND o.paymentStatus = :paymentStatus
      AND o.paymentExpiresAt <= :now
""")
    List<MasterOrder> findExpiredPaymentOrders(
            @Param("status") OrderStatus status,
            @Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("now") LocalDateTime now
    );
}
