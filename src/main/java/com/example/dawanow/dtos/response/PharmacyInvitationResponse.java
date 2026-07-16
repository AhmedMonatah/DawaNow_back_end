package com.example.dawanow.dtos.response;

import com.example.dawanow.entity.PharmacyInvitationStatus;
import java.time.Instant;

public record PharmacyInvitationResponse(
        Long id, Long pharmacyId, String pharmacyName, Long pharmacistId,
        String pharmacistFirstName, String pharmacistLastName,
        PharmacyInvitationStatus status, Instant createdAt
) {
}
