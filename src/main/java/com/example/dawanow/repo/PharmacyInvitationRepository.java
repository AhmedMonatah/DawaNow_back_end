package com.example.dawanow.repo;

import com.example.dawanow.entity.PharmacyInvitation;
import com.example.dawanow.entity.PharmacyInvitationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PharmacyInvitationRepository extends JpaRepository<PharmacyInvitation, Long> {
    @EntityGraph(attributePaths = {"pharmacy", "pharmacist"})
    Optional<PharmacyInvitation> findByPharmacyIdAndPharmacistIdAndStatus(
            Long pharmacyId, Long pharmacistId, PharmacyInvitationStatus status
    );

    @EntityGraph(attributePaths = {"pharmacy", "pharmacist"})
    List<PharmacyInvitation> findByPharmacistIdAndStatus(Long pharmacistId, PharmacyInvitationStatus status);

    @EntityGraph(attributePaths = {"pharmacy", "pharmacist"})
    List<PharmacyInvitation> findByPharmacyIdAndStatus(Long pharmacyId, PharmacyInvitationStatus status);
}
