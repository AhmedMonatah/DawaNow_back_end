package com.example.dawanow.dtos.request;

import com.example.dawanow.entity.notification.DeviceToken;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterDeviceTokenRequest(
        @NotBlank String fcmToken,
        @NotNull DeviceToken.Platform platform,
        @NotBlank String deviceId
) {
}
