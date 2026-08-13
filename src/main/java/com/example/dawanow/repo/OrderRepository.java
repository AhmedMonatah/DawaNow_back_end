package com.example.dawanow.repo;

import com.example.dawanow.entity.Order;
import com.example.dawanow.entity.OrderStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("""
            SELECT DISTINCT o FROM Order o
            JOIN FETCH o.user
            JOIN FETCH o.pharmacy
            JOIN FETCH o.pharmacist
            JOIN FETCH o.request
            JOIN FETCH o.offer
            JOIN FETCH o.masterOrder
            WHERE o.id = :id
            """)
    Optional<Order> findById(@Param("id") Long id);

    @Query("""
            SELECT DISTINCT o FROM Order o
            JOIN FETCH o.user
            JOIN FETCH o.pharmacy
            JOIN FETCH o.pharmacist
            JOIN FETCH o.request
            JOIN FETCH o.offer
            JOIN FETCH o.masterOrder
            WHERE o.masterOrder.id = :masterOrderId
            ORDER BY o.date DESC, o.id ASC
            """)
    List<Order> findByMasterOrderId(@Param("masterOrderId") Long masterOrderId);

    @Query("""
            SELECT DISTINCT o FROM Order o
            JOIN FETCH o.user
            JOIN FETCH o.pharmacy
            JOIN FETCH o.pharmacist
            JOIN FETCH o.request
            JOIN FETCH o.offer
            JOIN FETCH o.masterOrder
            WHERE o.masterOrder.id IN :masterOrderIds
            ORDER BY o.date DESC, o.id ASC
            """)
    List<Order> findByMasterOrderIdIn(@Param("masterOrderIds") List<Long> masterOrderIds);

    @Query(value = """
            SELECT DISTINCT o FROM Order o
            JOIN FETCH o.user
            JOIN FETCH o.pharmacy
            JOIN FETCH o.pharmacist
            JOIN FETCH o.request
            JOIN FETCH o.offer
            JOIN FETCH o.masterOrder
            WHERE o.pharmacy.id = :pharmacyId
              AND o.status = :status
            ORDER BY o.date DESC, o.id ASC
            """,
            countQuery = "SELECT COUNT(o) FROM Order o WHERE o.pharmacy.id = :pharmacyId AND o.status = :status")
    Page<Order> findByPharmacyIdAndStatus(@Param("pharmacyId") Long pharmacyId, @Param("status") OrderStatus status, Pageable pageable);

    @Query(value = """
            SELECT DISTINCT o FROM Order o
            JOIN FETCH o.user
            JOIN FETCH o.pharmacy
            JOIN FETCH o.pharmacist
            JOIN FETCH o.request
            JOIN FETCH o.offer
            JOIN FETCH o.masterOrder
            WHERE o.pharmacy.id = :pharmacyId
              AND o.status NOT IN :statuses
            ORDER BY o.date DESC, o.id ASC
            """,
            countQuery = "SELECT COUNT(o) FROM Order o WHERE o.pharmacy.id = :pharmacyId AND o.status NOT IN :statuses")
    Page<Order> findByPharmacyIdAndStatusNotIn(@Param("pharmacyId") Long pharmacyId, @Param("statuses") List<OrderStatus> statuses, Pageable pageable);

    @Query(value = """
            SELECT DISTINCT o FROM Order o
            JOIN FETCH o.user
            JOIN FETCH o.pharmacy
            JOIN FETCH o.pharmacist
            JOIN FETCH o.request
            JOIN FETCH o.offer
            JOIN FETCH o.masterOrder
            WHERE o.status = :status
            ORDER BY o.date DESC, o.id ASC
            """,
            countQuery = "SELECT COUNT(o) FROM Order o WHERE o.status = :status")
    Page<Order> findByStatus(@Param("status") OrderStatus status, Pageable pageable);

    @Query(value = """
            SELECT DISTINCT o FROM Order o
            JOIN FETCH o.user
            JOIN FETCH o.pharmacy
            JOIN FETCH o.pharmacist
            JOIN FETCH o.request
            JOIN FETCH o.offer
            JOIN FETCH o.masterOrder
            WHERE o.status NOT IN :statuses
            ORDER BY o.date DESC, o.id ASC
            """,
            countQuery = "SELECT COUNT(o) FROM Order o WHERE o.status NOT IN :statuses")
    Page<Order> findByStatusNotIn(@Param("statuses") List<OrderStatus> statuses, Pageable pageable);

    long countByPharmacyIdAndDateBetween(Long pharmacyId, LocalDateTime start, LocalDateTime end);

    @Query("""
            SELECT DISTINCT o FROM Order o
            JOIN FETCH o.user
            JOIN FETCH o.pharmacy
            JOIN FETCH o.pharmacist
            JOIN FETCH o.request
            JOIN FETCH o.offer
            JOIN FETCH o.masterOrder
            WHERE o.pharmacy.id = :pharmacyId
              AND o.date BETWEEN :start AND :end
            ORDER BY o.date DESC
            """)
    List<Order> findByPharmacyIdAndDateBetweenOrderByDateDesc(
            @Param("pharmacyId") Long pharmacyId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT COALESCE(SUM(o.totalPrice), 0)
            FROM Order o
            WHERE o.pharmacy.id = :pharmacyId
              AND o.date BETWEEN :start AND :end
            """)
    BigDecimal sumTotalPriceByPharmacyIdAndDateBetween(
            @Param("pharmacyId") Long pharmacyId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
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
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable
    );

}
