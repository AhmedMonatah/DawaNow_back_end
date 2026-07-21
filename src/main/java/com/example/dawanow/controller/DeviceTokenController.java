package com.example.dawanow.controller;

import com.example.dawanow.dtos.request.RegisterDeviceTokenRequest;
import com.example.dawanow.dtos.response.ApiResponse;
import com.example.dawanow.service.CurrentPharmacistProvider;
import com.example.dawanow.service.DeviceTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pharmacists/me/devices")
@RequiredArgsConstructor
@Tag(name = "Device Tokens", description = "FCM device token registration for push notifications")
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;
    private final CurrentPharmacistProvider currentPharmacist;

    @PostMapping("/token")
    @Operation(
            summary = "Register a device token",
            description = "Registers an FCM push token for the currently authenticated pharmacist. "
                    + "Tokens are used to deliver real-time push notifications.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Token registered successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "Invalid request body"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Authentication is required"
            )
    })
    public ResponseEntity<ApiResponse<Void>> registerToken(@Valid @RequestBody RegisterDeviceTokenRequest request) {
        Long pharmacistId = currentPharmacist.get().getId();
        deviceTokenService.register(pharmacistId, request);
        return ResponseEntity.ok(ApiResponse.success("Token registered successfully"));
    }

    @DeleteMapping("/token")
    @Operation(
            summary = "Unregister a device token",
            description = "Removes an FCM push token so the pharmacist no longer receives notifications on that device.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Token unregistered successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Authentication is required"
            )
    })
    public ResponseEntity<ApiResponse<Void>> unregisterToken(@RequestParam String fcmToken) {
        Long pharmacistId = currentPharmacist.get().getId();
        deviceTokenService.unregister(pharmacistId, fcmToken);
        return ResponseEntity.ok(ApiResponse.success("Token unregistered successfully"));
    }
}
