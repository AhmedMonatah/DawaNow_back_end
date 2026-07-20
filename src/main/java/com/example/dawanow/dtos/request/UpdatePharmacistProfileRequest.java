package com.example.dawanow.dtos.request;

import java.time.LocalDate;

public record UpdatePharmacistProfileRequest(
        String firstName,
        String lastName,
        String homeAddress,
        LocalDate dob
) {
}
