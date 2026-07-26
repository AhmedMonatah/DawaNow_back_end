package com.example.dawanow.dtos.request;

import jakarta.validation.constraints.Email;
import java.time.LocalDate;

public record UpdateUserRequest(
        @Email String email,
        String firstName,
        String lastName,
        String homeAddress,
        LocalDate dob,
        Double deliveryLatitude,
        Double deliveryLongitude
) {
}
