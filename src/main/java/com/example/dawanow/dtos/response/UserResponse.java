package com.example.dawanow.dtos.response;

import com.example.dawanow.entity.UserRole;
import java.time.LocalDate;

public record UserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        UserRole role,
        String homeAddress,
        LocalDate dob,
        Double Lattitude,
        Double Longitude
) {
}
