package com.example.dawanow.repo;

import com.example.dawanow.entity.Pharmacist;
import com.example.dawanow.entity.OfferStatus;
import com.example.dawanow.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Fixed, read-only queries available to the pharmacy analytics agent. */
public interface AiPharmacyAnalyticsRepository extends Repository<Pharmacist, Long> {

    @EntityGraph(attributePaths = {"pharmacy", "pharmacy.adminPharmacist"})
    Optional<Pharmacist> findById(Long id);

    @Query("""
            SELECT pharmacist
            FROM Pharmacist pharmacist
            WHERE pharmacist.pharmacy.id = :pharmacyId
            ORDER BY pharmacist.id ASC
            """)
    List<Pharmacist> findCurrentPharmacists(@Param("pharmacyId") Long pharmacyId);

    @Query("""
            SELECT COUNT(assignment.id)
            FROM PharmacyAssignment assignment
            WHERE assignment.pharmacy.id = :pharmacyId
              AND assignment.assignedAt >= :start AND assignment.assignedAt < :end
            """)
    long countRequests(
            @Param("pharmacyId") Long pharmacyId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT COUNT(DISTINCT assignment.medicineRequest.id)
            FROM PharmacyAssignment assignment
            WHERE assignment.pharmacy.id = :pharmacyId
              AND assignment.assignedAt >= :start AND assignment.assignedAt < :end
              AND EXISTS (
                  SELECT offer.id FROM PharmacyOffer offer
                  WHERE offer.pharmacy.id = :pharmacyId
                    AND offer.request.id = assignment.medicineRequest.id
              )
            """)
    long countCoveredRequests(
            @Param("pharmacyId") Long pharmacyId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT offer.status AS status, COUNT(offer.id) AS activityCount
            FROM PharmacyOffer offer
            WHERE offer.pharmacy.id = :pharmacyId
              AND (:pharmacistId IS NULL OR offer.pharmacist.id = :pharmacistId)
              AND offer.createdAt >= :start AND offer.createdAt < :end
            GROUP BY offer.status
            """)
    List<OfferStatusCountProjection> countOffersByStatus(
            @Param("pharmacyId") Long pharmacyId,
            @Param("pharmacistId") Long pharmacistId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT customerOrder.status AS status, COUNT(customerOrder.id) AS activityCount
            FROM Order customerOrder
            WHERE customerOrder.pharmacy.id = :pharmacyId
              AND (:pharmacistId IS NULL OR customerOrder.pharmacist.id = :pharmacistId)
              AND customerOrder.date >= :start AND customerOrder.date < :end
            GROUP BY customerOrder.status
            """)
    List<OrderStatusCountProjection> countOrdersByStatus(
            @Param("pharmacyId") Long pharmacyId,
            @Param("pharmacistId") Long pharmacistId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT COALESCE(SUM(customerOrder.totalPrice), 0)
            FROM Order customerOrder
            WHERE customerOrder.pharmacy.id = :pharmacyId
              AND (:pharmacistId IS NULL OR customerOrder.pharmacist.id = :pharmacistId)
              AND customerOrder.date >= :start AND customerOrder.date < :end
            """)
    BigDecimal sumOrderValue(
            @Param("pharmacyId") Long pharmacyId,
            @Param("pharmacistId") Long pharmacistId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT COALESCE(SUM(customerOrder.totalPrice), 0)
            FROM Order customerOrder
            WHERE customerOrder.pharmacy.id = :pharmacyId
              AND (:pharmacistId IS NULL OR customerOrder.pharmacist.id = :pharmacistId)
              AND customerOrder.status = com.example.dawanow.entity.OrderStatus.DELIVERED
              AND customerOrder.date >= :start AND customerOrder.date < :end
            """)
    BigDecimal sumDeliveredRevenue(
            @Param("pharmacyId") Long pharmacyId,
            @Param("pharmacistId") Long pharmacistId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT COALESCE(AVG(customerOrder.totalPrice), 0)
            FROM Order customerOrder
            WHERE customerOrder.pharmacy.id = :pharmacyId
              AND (:pharmacistId IS NULL OR customerOrder.pharmacist.id = :pharmacistId)
              AND customerOrder.date >= :start AND customerOrder.date < :end
            """)
    BigDecimal averageOrderValue(
            @Param("pharmacyId") Long pharmacyId,
            @Param("pharmacistId") Long pharmacistId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT customerOrder.id AS orderId,
                   customerOrder.status AS status,
                   customerOrder.totalPrice AS totalPrice,
                   customerOrder.date AS orderDate
            FROM Order customerOrder
            WHERE customerOrder.pharmacy.id = :pharmacyId
              AND (:pharmacistId IS NULL OR customerOrder.pharmacist.id = :pharmacistId)
              AND customerOrder.date >= :start AND customerOrder.date < :end
            ORDER BY customerOrder.totalPrice DESC, customerOrder.id ASC
            """)
    List<OrderHighlightProjection> findLargestOrders(
            @Param("pharmacyId") Long pharmacyId,
            @Param("pharmacistId") Long pharmacistId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable
    );

    @Query("""
            SELECT pharmacist.id AS pharmacistId,
                   pharmacist.firstName AS firstName,
                   pharmacist.lastName AS lastName,
                   COUNT(customerOrder.id) AS activityCount
            FROM Order customerOrder
            JOIN customerOrder.pharmacist pharmacist
            WHERE customerOrder.pharmacy.id = :pharmacyId
              AND pharmacist.pharmacy.id = :pharmacyId
              AND pharmacist.id <> :adminPharmacistId
              AND customerOrder.date >= :start AND customerOrder.date < :end
            GROUP BY pharmacist.id, pharmacist.firstName, pharmacist.lastName
            """)
    List<PharmacistCountProjection> countGeneratedOrdersByRegularPharmacist(
            @Param("pharmacyId") Long pharmacyId,
            @Param("adminPharmacistId") Long adminPharmacistId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT item.product.id AS productId,
                   item.product.productName AS productName,
                   SUM(item.quantity) AS quantity,
                   COUNT(DISTINCT customerOrder.id) AS orderCount,
                   SUM(item.quantity * item.unitPrice) AS revenue
            FROM OrderItem item
            JOIN item.order customerOrder
            WHERE customerOrder.pharmacy.id = :pharmacyId
              AND (:pharmacistId IS NULL OR customerOrder.pharmacist.id = :pharmacistId)
              AND customerOrder.status = com.example.dawanow.entity.OrderStatus.DELIVERED
              AND customerOrder.date >= :start AND customerOrder.date < :end
              AND (:productQuery IS NULL
                   OR LOWER(item.product.name) LIKE :productQuery
                   OR LOWER(item.product.productName) LIKE :productQuery)
            GROUP BY item.product.id, item.product.productName
            ORDER BY SUM(item.quantity) DESC, item.product.id ASC
            """)
    List<TopProductProjection> findTopDeliveredProducts(
            @Param("pharmacyId") Long pharmacyId,
            @Param("pharmacistId") Long pharmacistId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("productQuery") String productQuery,
            Pageable pageable
    );

    interface OfferStatusCountProjection {
        OfferStatus getStatus();
        Long getActivityCount();
    }

    interface OrderStatusCountProjection {
        OrderStatus getStatus();
        Long getActivityCount();
    }

    interface OrderHighlightProjection {
        Long getOrderId();
        OrderStatus getStatus();
        BigDecimal getTotalPrice();
        LocalDateTime getOrderDate();
    }

    interface PharmacistCountProjection {
        Long getPharmacistId();
        String getFirstName();
        String getLastName();
        Long getActivityCount();
    }

    interface TopProductProjection {
        Long getProductId();
        String getProductName();
        Long getQuantity();
        Long getOrderCount();
        BigDecimal getRevenue();
    }
}
