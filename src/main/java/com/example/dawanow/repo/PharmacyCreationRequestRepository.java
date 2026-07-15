package com.example.dawanow.repo;

import com.example.dawanow.entity.PharmacyCreationRequest;
import com.example.dawanow.entity.PharmacyCreationRequestStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PharmacyCreationRequestRepository extends JpaRepository<PharmacyCreationRequest, Long> {
    List<PharmacyCreationRequest> findByStatus(PharmacyCreationRequestStatus status);

    Optional<PharmacyCreationRequest> findByPharmacistIdAndStatus(Long pharmacistId, PharmacyCreationRequestStatus status);
}
