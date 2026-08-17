package com.example.dawanow.dtos.request;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @NotBlank(message = "Reset token is required") String resetToken,
        @NotBlank(message = "New password is required") String newPassword
) {
}
