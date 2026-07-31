package com.example.dawanow.repo;

import com.example.dawanow.entity.Order;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByUserId(Long userId, Pageable pageable);

    Page<Order> findByPharmacyId(Long pharmacyId, Pageable pageable);

    boolean existsByOfferId(Long offerId);

    boolean existsByOfferRequestId(Long requestId);

    long countByPharmacyIdAndDateBetween(Long pharmacyId, LocalDate start, LocalDate end);

    List<Order> findByPharmacyIdAndDateBetweenOrderByDateDesc(Long pharmacyId, LocalDate start, LocalDate end);

    @Query("""
            SELECT COALESCE(SUM(o.totalPrice), 0)
            FROM Order o
            WHERE o.pharmacy.id = :pharmacyId
              AND o.date BETWEEN :start AND :end
            """)
    BigDecimal sumTotalPriceByPharmacyIdAndDateBetween(
            @Param("pharmacyId") Long pharmacyId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    @Query("""
            SELECT oi.product.id AS productId,
                   oi.product.name AS productName,
                   oi.product.imageUrl AS imageUrl,
                   SUM(oi.quantity) AS totalQuantity,
                   SUM(oi.unitPrice * oi.quantity) AS totalRevenue
            FROM OrderItem oi
            JOIN oi.order o
            WHERE o.pharmacy.id = :pharmacyId
              AND o.date BETWEEN :start AND :end
            GROUP BY oi.product.id, oi.product.name, oi.product.imageUrl
            ORDER BY totalQuantity DESC
            """)
    List<Object[]> findTopSellingProducts(
            @Param("pharmacyId") Long pharmacyId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            Pageable pageable
    );

    Optional<Order> findByPaymentIntentId(String paymentIntentId);
}
