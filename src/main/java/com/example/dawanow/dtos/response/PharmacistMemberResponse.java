package com.example.dawanow.dtos.response;

import com.example.dawanow.entity.Pharmacist;

public record PharmacistMemberResponse(
        Long id,
        String firstName,
        String lastName,
        String phoneNumber,
        String email,
        boolean isAdmin
) {
    public static PharmacistMemberResponse from(Pharmacist pharmacist, Pharmacist admin) {
        return new PharmacistMemberResponse(
                pharmacist.getId(),
                pharmacist.getFirstName(),
                pharmacist.getLastName(),
                pharmacist.getPhoneNumber(),
                pharmacist.getEmail(),
                pharmacist.getId().equals(admin.getId())
        );
    }
}
