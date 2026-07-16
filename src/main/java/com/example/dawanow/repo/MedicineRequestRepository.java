package com.example.dawanow.repo;

import com.example.dawanow.entity.MedicineRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicineRequestRepository extends JpaRepository<MedicineRequest, Long> {

    Page<MedicineRequest> findByCustomerId(Long customerId, Pageable pageable);

    Page<MedicineRequest> findDistinctByOffers_Pharmacy_Id(Long pharmacyId, Pageable pageable);

    boolean existsByIdAndOffers_Pharmacy_Id(Long requestId, Long pharmacyId);
}
