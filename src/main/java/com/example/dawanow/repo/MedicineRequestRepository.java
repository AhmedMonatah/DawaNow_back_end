package com.example.dawanow.repo;

import com.example.dawanow.entity.MedicineRequest;
import com.example.dawanow.entity.RequestStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;

public interface MedicineRequestRepository extends JpaRepository<MedicineRequest, Long> {

    Page<MedicineRequest> findByCustomerId(Long customerId, Pageable pageable);

    Page<MedicineRequest> findDistinctByOffers_Pharmacy_Id(Long pharmacyId, Pageable pageable);

    boolean existsByIdAndOffers_Pharmacy_Id(Long requestId, Long pharmacyId);

    List<MedicineRequest> findByStatus(RequestStatus status);

    List<MedicineRequest> findByStatusAndExpiresAtBefore(RequestStatus status, java.time.LocalDateTime expiresAt);

    @EntityGraph(attributePaths = {"customer", "items", "items.product"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MedicineRequest> findDetailedById(Long id);
}
