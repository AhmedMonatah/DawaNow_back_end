package com.example.dawanow.repo;

import com.example.dawanow.entity.PharmacyAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface PharmacyAssignmentRepository extends JpaRepository<PharmacyAssignment,Long> {


    @EntityGraph(attributePaths = {"medicineRequest"})
    Page<PharmacyAssignment> getPharmacyAssignmentsByPharmacy_Id(Long pharmacyId, Pageable pageable);
}
