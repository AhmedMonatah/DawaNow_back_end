package com.example.dawanow.dtos.response;

import com.example.dawanow.entity.PharmacyCreationRequestStatus;
import java.time.Instant;

public record PharmacyCreationRequestResponse(
        Long id,
        Long pharmacistId,
        String pharmacistName,
        String name,
        Double latitude,
        Double longitude,
        String address,
        String phoneNumber,
        String licenseNumber,
        String licenseDocumentPath,
        PharmacyCreationRequestStatus status,
        Long reviewedById,
        Long approvedPharmacyId,
        Instant createdAt,
        Instant updatedAt
) {
}
