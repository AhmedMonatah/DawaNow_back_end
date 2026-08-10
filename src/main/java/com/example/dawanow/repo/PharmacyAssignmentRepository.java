package com.example.dawanow.repo;

import com.example.dawanow.entity.AssignmentStatus;
import com.example.dawanow.entity.PharmacyAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


public interface PharmacyAssignmentRepository extends JpaRepository<PharmacyAssignment,Long> {


    @EntityGraph(attributePaths = {"medicineRequest", "medicineRequest.customer"})
    Page<PharmacyAssignment> getPharmacyAssignmentsByPharmacy_Id(Long pharmacyId, Pageable pageable);

    @EntityGraph(attributePaths = {"medicineRequest", "medicineRequest.customer"})
    Page<PharmacyAssignment> getPharmacyAssignmentsByPharmacy_IdAndStatus(Long pharmacyId, AssignmentStatus status, Pageable pageable);

    boolean existsByMedicineRequest_IdAndPharmacy_Id(Long requestId, Long pharmacyId);

    Optional<PharmacyAssignment> findByPharmacyIdAndMedicineRequestId(Long pharmacyId, Long medicineRequestId);

    List<PharmacyAssignment> findByMedicineRequest_IdIn(List<Long> requestIds);

    long countByPharmacyIdAndAssignedAtBetween(Long pharmacyId, LocalDateTime start, LocalDateTime end);
}
