package com.example.dawanow.controller;

import com.example.dawanow.dtos.request.*;
import com.example.dawanow.dtos.response.ApiResponse;
import com.example.dawanow.dtos.response.AuthResponse;
import com.example.dawanow.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Public registration and login for all user roles")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(
            summary = "Register a new account",
            description = "Public endpoint. Creates a new user account with the specified role (CUSTOMER, PHARMACIST, or ADMIN)."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Registration successful; tokens and user info returned"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed or email/phone already in use"
            )
    })
    public ResponseEntity<ApiResponse<Void>> register(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Registration details including email, password, and role",
                    required = true
            )
            @Valid @RequestBody RegisterRequest request
    ) {
        authService.register(request);
        return ResponseEntity.ok(ApiResponse.success("Registration initiated successfully."));
    }

    @PostMapping("/login")
    @Operation(
            summary = "Log in",
            description = "Public endpoint. Authenticates with email and password, then returns access and refresh tokens."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Login successful; tokens and user info returned"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid email or password"
            )
    })
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Login credentials",
                    required = true
            )
            @Valid @RequestBody LoginRequest request
    ) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Logged in successfully", response));
    }
    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh access token",
            description = "Public endpoint. Uses a valid refresh token to generate a fresh, short-lived access token and a rotated refresh token."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Tokens refreshed successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Invalid, expired, or tampered refresh token"
            )
    })
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "The refresh token payload",
                    required = true
            )
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        AuthResponse response = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.success("Refreshed successfully", response));
    }

    @PostMapping("/verify")
    @Operation(
            summary = "Verify account with OTP",
            description = "Public endpoint. Verifies the user's pending registration using the 6-digit OTP sent to their email. On success, finalizes registration in the database and returns access/refresh tokens."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Account verified successfully; session tokens returned"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid OTP, expired registration session, or account already verified"
            )
    })
    public ResponseEntity<ApiResponse<AuthResponse>> verify(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Email and the matching 6-digit OTP code",
                    required = true
            )
            @Valid @RequestBody VerifyRequest request
    ) {
        AuthResponse response = authService.verify(request);
        return ResponseEntity.ok(ApiResponse.success("Verified successfully", response));
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Log out user",
            description = "Public endpoint. Revokes and invalidates the provided refresh token session to prevent future token renewals."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Logged out successfully; session invalidated"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Token invalid, already blacklisted, or not found"
            )
    })
    public ResponseEntity<ApiResponse<Void>> logout(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "The refresh token to be invalidated",
                    required = true
            )
            @Valid @RequestBody LogoutRequest request
    ) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
    }
}