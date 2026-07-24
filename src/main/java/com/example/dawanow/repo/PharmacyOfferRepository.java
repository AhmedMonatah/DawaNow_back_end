package com.example.dawanow.repo;

import com.example.dawanow.entity.PharmacyOffer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PharmacyOfferRepository extends JpaRepository<PharmacyOffer, Long> {
    boolean existsByPharmacyIdAndRequestId(Long pharmacyId, Long medicineId);
    Page<PharmacyOffer> findByRequestIdOrderByDistanceKmAsc(Long requestId, Pageable pageable);


    Optional<PharmacyOffer> findById(Long id);

    List<PharmacyOffer> findByRequestId(Long requestId);

}
