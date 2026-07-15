package com.example.dawanow.repo;

import com.example.dawanow.entity.Pharmacy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PharmacyRepository extends JpaRepository<Pharmacy, Long> {
    boolean existsByAdminPharmacistId(Long pharmacistId);
}
