package com.example.dawanow.repo;

import com.example.dawanow.entity.Pharmacist;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PharmacistRepository extends JpaRepository<Pharmacist, Long> {
    Optional<Pharmacist> findByEmail(String email);
}
