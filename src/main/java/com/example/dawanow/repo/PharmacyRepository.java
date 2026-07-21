package com.example.dawanow.repo;

import com.example.dawanow.entity.Pharmacy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PharmacyRepository extends JpaRepository<Pharmacy, Long> {
    boolean existsByAdminPharmacistId(Long pharmacistId);

    Optional<Pharmacy> findByName(String name);

    @Query(
            value = """
                 SELECT p.*, (
                     6371 * acos(
                         cos(radians(:latitude)) * cos(radians(p.latitude)) *
                         cos(radians(p.longitude) - radians(:longitude)) +
                         sin(radians(:latitude)) * sin(radians(p.latitude))
                     )
                 ) AS distance
                 FROM pharmacy p
                 WHERE (
                     6371 * acos(
                         cos(radians(:latitude)) * cos(radians(p.latitude)) *
                         cos(radians(p.longitude) - radians(:longitude)) +
                         sin(radians(:latitude)) * sin(radians(p.latitude))
                     )
                 ) <= :radius
                 ORDER BY distance ASC
                 """,
            nativeQuery = true
    )
    List<Pharmacy> findNearbyPharmacies( @Param("latitude") double latitude,
                                         @Param("longitude") double longitude,
                                         @Param("radius") double radius);

}
