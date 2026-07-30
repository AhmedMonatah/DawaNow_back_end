package com.example.dawanow.repo;

import com.example.dawanow.entity.Order;
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
            WHERE o.user.id = :userId
            """)
    List<Order> findByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT o.request.id FROM Order o
            WHERE o.user.id = :userId
            ORDER BY o.request.id DESC
            """)
    List<Long> findRequestIdsByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            SELECT COUNT(DISTINCT o.request.id) FROM Order o
            WHERE o.user.id = :userId
            """)
    long countDistinctRequestIdsByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT o FROM Order o
            JOIN FETCH o.user
            JOIN FETCH o.pharmacy
            JOIN FETCH o.pharmacist
            JOIN FETCH o.request
            JOIN FETCH o.offer
            WHERE o.user.id = :userId AND o.request.id IN :requestIds
            ORDER BY o.request.id DESC, o.date DESC
            """)
    List<Order> findByUserIdAndRequestIdIn(@Param("userId") Long userId, @Param("requestIds") List<Long> requestIds);

    @Query(value = """
            SELECT DISTINCT o FROM Order o
            JOIN FETCH o.user
            JOIN FETCH o.pharmacy
            JOIN FETCH o.pharmacist
            JOIN FETCH o.request
            JOIN FETCH o.offer
            WHERE o.pharmacy.id = :pharmacyId
            """,
            countQuery = "SELECT COUNT(o) FROM Order o WHERE o.pharmacy.id = :pharmacyId")
    Page<Order> findByPharmacyId(@Param("pharmacyId") Long pharmacyId, Pageable pageable);

    boolean existsByOfferId(Long offerId);

    boolean existsByOfferRequestId(Long requestId);

    long countByPharmacyIdAndDateBetween(Long pharmacyId, LocalDateTime start, LocalDateTime end);

    @Query("""
            SELECT DISTINCT o FROM Order o
            JOIN FETCH o.user
            JOIN FETCH o.pharmacy
            JOIN FETCH o.pharmacist
            JOIN FETCH o.request
            JOIN FETCH o.offer
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
