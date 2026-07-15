package com.example.dawanow.dtos.response;

public record PharmacyResponse(
        Long id,
        String name,
        Double latitude,
        Double longitude,
        String address,
        String phoneNumber,
        String licenseNumber,
        String licenseDocumentPath,
        Long adminPharmacistId
) {
}
