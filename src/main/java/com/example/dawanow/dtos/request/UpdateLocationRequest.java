package com.example.dawanow.dtos.request;

import jakarta.validation.constraints.NotNull;

public record UpdateLocationRequest(
        @NotNull Double latitude,
        @NotNull Double longitude
) {}