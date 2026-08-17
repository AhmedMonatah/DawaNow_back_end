package com.example.dawanow.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record VerifyResetOtpRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid") String email,
        @NotBlank(message = "OTP code is required") String otpCode
) {
}
