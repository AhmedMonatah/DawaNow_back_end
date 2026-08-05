package com.example.dawanow.repo;

import com.example.dawanow.entity.Pharmacist;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read-only aggregate queries owned by the AI pharmacist-performance feature. */
public interface AiPharmacistPerformanceRepository extends Repository<Pharmacist, Long> {

    @EntityGraph(attributePaths = {"pharmacy", "pharmacy.adminPharmacist"})
    Optional<Pharmacist> findById(Long id);

    @Query("""
            SELECT pharmacist
            FROM Pharmacist pharmacist
            WHERE pharmacist.pharmacy.id = :pharmacyId
              AND pharmacist.id <> :adminPharmacistId
            ORDER BY pharmacist.id ASC
            """)
    List<Pharmacist> findCurrentRegularPharmacists(
            @Param("pharmacyId") Long pharmacyId,
            @Param("adminPharmacistId") Long adminPharmacistId
    );

    @Query("""
            SELECT pharmacist.id AS pharmacistId,
                   pharmacist.firstName AS firstName,
                   pharmacist.lastName AS lastName,
                   COUNT(offer.id) AS activityCount
            FROM PharmacyOffer offer
            JOIN offer.pharmacist pharmacist
            WHERE offer.pharmacy.id = :pharmacyId
              AND pharmacist.pharmacy.id = :pharmacyId
              AND pharmacist.id <> :adminPharmacistId
              AND offer.createdAt BETWEEN :start AND :end
            GROUP BY pharmacist.id, pharmacist.firstName, pharmacist.lastName
            ORDER BY COUNT(offer.id) DESC, pharmacist.id ASC
            """)
    List<PharmacistPerformanceProjection> findTopOfferCreators(
            @Param("pharmacyId") Long pharmacyId,
            @Param("adminPharmacistId") Long adminPharmacistId,
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
            JOIN customerOrder.offer selectedOffer
            JOIN selectedOffer.pharmacist pharmacist
            WHERE customerOrder.pharmacy.id = :pharmacyId
              AND selectedOffer.pharmacy.id = :pharmacyId
              AND pharmacist.pharmacy.id = :pharmacyId
              AND pharmacist.id <> :adminPharmacistId
              AND customerOrder.date BETWEEN :start AND :end
            GROUP BY pharmacist.id, pharmacist.firstName, pharmacist.lastName
            ORDER BY COUNT(customerOrder.id) DESC, pharmacist.id ASC
            """)
    List<PharmacistPerformanceProjection> findTopSuccessfulOrderCreators(
            @Param("pharmacyId") Long pharmacyId,
            @Param("adminPharmacistId") Long adminPharmacistId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable
    );

    interface PharmacistPerformanceProjection {
        Long getPharmacistId();

        String getFirstName();

        String getLastName();

        Long getActivityCount();
    }
}
