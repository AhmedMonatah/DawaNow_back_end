package com.example.dawanow.dtos.response;

import com.example.dawanow.entity.Pharmacist;
import com.example.dawanow.entity.Pharmacy;
import java.util.List;

public record PharmacyMineResponse(
        Long id,
        String name,
        Double latitude,
        Double longitude,
        String address,
        String phoneNumber,
        boolean isAdmin,
        List<PharmacistMemberResponse> pharmacists
) {
    public static PharmacyMineResponse from(Pharmacy pharmacy, Pharmacist pharmacist) {
        Pharmacist admin = pharmacy.getAdminPharmacist();
        return new PharmacyMineResponse(
                pharmacy.getId(),
                pharmacy.getName(),
                pharmacy.getLatitude(),
                pharmacy.getLongitude(),
                pharmacy.getAddress(),
                pharmacy.getPhoneNumber(),
                admin.getId().equals(pharmacist.getId()),
                pharmacy.getPharmacists().stream()
                        .map(p -> PharmacistMemberResponse.from(p, admin))
                        .toList()
        );
    }
}
