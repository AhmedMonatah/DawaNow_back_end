package com.example.dawanow.dtos.request;

import com.example.dawanow.entity.notification.DeviceToken;
import jakarta.validation.constraints.NotBlank;

public record RegisterDeviceTokenRequest(
        @NotBlank String fcmToken,
        @NotBlank DeviceToken.Platform platform,
        @NotBlank String deviceId
) {
}
