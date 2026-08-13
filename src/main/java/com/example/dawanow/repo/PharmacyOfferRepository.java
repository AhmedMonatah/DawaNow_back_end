package com.example.dawanow.repo;

import com.example.dawanow.entity.OfferStatus;
import com.example.dawanow.entity.PharmacyOffer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PharmacyOfferRepository extends JpaRepository<PharmacyOffer, Long> {
    boolean existsByPharmacyIdAndRequestId(Long pharmacyId, Long medicineId);

    @EntityGraph(attributePaths = {"request", "pharmacy", "pharmacist"})
    Page<PharmacyOffer> findByRequestIdOrderByDistanceKmAsc(Long requestId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"request", "pharmacy", "pharmacist"})
    Optional<PharmacyOffer> findById(Long id);

    @EntityGraph(attributePaths = {"request", "pharmacy", "pharmacist"})
    List<PharmacyOffer> findByRequestId(Long requestId);

    long countByPharmacyIdAndCreatedAtBetween(Long pharmacyId, LocalDateTime start, LocalDateTime end);

    long countByPharmacyIdAndStatusInAndCreatedAtBetween(
            Long pharmacyId,
            List<OfferStatus> statuses,
            LocalDateTime start,
            LocalDateTime end
    );
}
