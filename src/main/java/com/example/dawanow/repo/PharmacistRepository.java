package com.example.dawanow.repo;

import com.example.dawanow.entity.Pharmacist;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PharmacistRepository extends JpaRepository<Pharmacist, Long> {
    Optional<Pharmacist> findByEmail(String email);
    @Query(
            value = """
        SELECT u.* FROM user u
        JOIN pharmacist_presence pp ON pp.pharmacist_id = u.id
        WHERE u.pharmacy_id IN :pharmacyIds
        AND pp.is_on_duty = true
        """,
            nativeQuery = true
    )
    List<Pharmacist> findOnDutyByPharmacyIdIn(@Param("pharmacyIds") List<Long> pharmacyIds);
}
