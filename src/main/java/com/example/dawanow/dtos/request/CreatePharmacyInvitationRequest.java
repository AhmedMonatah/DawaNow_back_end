package com.example.dawanow.dtos.request;

import jakarta.validation.constraints.NotNull;

public record CreatePharmacyInvitationRequest(@NotNull Long pharmacistId) {
}
