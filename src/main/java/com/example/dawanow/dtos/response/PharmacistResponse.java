package com.example.dawanow.dtos.response;

public record PharmacistResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        Long pharmacyId,
        boolean pharmacyAdmin
) {
}
