package com.example.dawanow.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreatePharmacyInvitationRequest(
        @NotBlank @Email String email
) {
}
