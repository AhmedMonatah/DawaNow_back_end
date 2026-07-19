package com.example.dawanow.dtos.request;

import com.example.dawanow.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid") String email,
        @NotBlank(message = "Phone number is required")
        @Pattern(
                regexp = "^01[0125][0-9]{8}$",
                message = "Phone number must be a valid Egyptian mobile number"
        ) String phoneNumber,
        @NotBlank(message = "First name is required") String firstName,
        @NotBlank(message = "Last name is required") String lastName,
        @NotBlank(message = "Password is required") String password,
        @NotNull UserRole role,
        String homeAddress,
        LocalDate dob,
        Long pharmacyId
) {
}
