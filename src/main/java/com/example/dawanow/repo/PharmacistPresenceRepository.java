package com.example.dawanow.repo;

import com.example.dawanow.entity.PharmacistPresence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PharmacistPresenceRepository extends JpaRepository<PharmacistPresence, Long> {

    Optional<PharmacistPresence> findByPharmacistId(Long pharmacistId);

    // used by order-broadcast fan-out: on-duty pharmacists belonging to a given pharmacy.
    // each pharmacist belongs to at most one pharmacy, so this is a direct join,
    // no staff/membership table needed.
    @Query("""
        SELECT ph.id FROM Pharmacist ph
        JOIN PharmacistPresence p ON p.pharmacistId = ph.id
        WHERE ph.pharmacy.id = :pharmacyId AND p.onDuty = true
    """)
    List<Long> findOnDutyPharmacistIdsForPharmacy(@Param("pharmacyId") Long pharmacyId);

    @Modifying
    @Query("""
        UPDATE PharmacistPresence p SET p.onDuty = false, p.updatedAt = CURRENT_TIMESTAMP
        WHERE p.onDuty = true AND p.lastHeartbeatAt < :threshold
    """)
    int flipStaleToOffDuty(@Param("threshold") Instant threshold);
}
