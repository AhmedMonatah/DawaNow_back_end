package com.example.dawanow.dtos.response;

import java.time.LocalDate;

public record CustomerResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String homeAddress,
        LocalDate dob,
        String phoneNumber
) {
}
