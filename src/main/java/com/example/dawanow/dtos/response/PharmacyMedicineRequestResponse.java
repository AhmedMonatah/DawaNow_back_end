package com.example.dawanow.dtos.response;

import com.example.dawanow.entity.AssignmentStatus;

public record PharmacyMedicineRequestResponse(
        MedicineRequestResponse request,
        AssignmentStatus assignmentStatus,
        Double distanceKm
) {
}