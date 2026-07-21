package com.example.dawanow.controller;

import com.example.dawanow.dtos.response.ApiResponse;
import com.example.dawanow.dtos.response.PresenceStatusResponse;
import com.example.dawanow.service.CurrentPharmacistProvider;
import com.example.dawanow.service.PresenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pharmacists/me/presence")
@RequiredArgsConstructor
@Tag(name = "Pharmacist Presence", description = "On-duty toggle and heartbeat for the authenticated pharmacist")
public class PharmacistPresenceController {

    private final PresenceService presenceService;
    private final CurrentPharmacistProvider currentPharmacist;

    @PostMapping("/on-duty")
    @Operation(
            summary = "Go on duty",
            description = "Marks the currently authenticated pharmacist as on duty. "
                    + "An on-duty pharmacist becomes eligible to receive new medicine requests in their area.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Pharmacist is now on duty"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Authentication is required"
            )
    })
    public ResponseEntity<ApiResponse<PresenceStatusResponse>> goOnDuty() {
        Long pharmacistId = currentPharmacist.get().getId();
        return ResponseEntity.ok(ApiResponse.success("On duty", presenceService.goOnDuty(pharmacistId)));
    }

    @PostMapping("/off-duty")
    @Operation(
            summary = "Go off duty",
            description = "Marks the currently authenticated pharmacist as off duty. "
                    + "The pharmacist will stop receiving new medicine requests.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Pharmacist is now off duty"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Authentication is required"
            )
    })
    public ResponseEntity<ApiResponse<PresenceStatusResponse>> goOffDuty() {
        Long pharmacistId = currentPharmacist.get().getId();
        return ResponseEntity.ok(ApiResponse.success("Off duty", presenceService.goOffDuty(pharmacistId)));
    }

    @PostMapping("/heartbeat")
    @Operation(
            summary = "Send heartbeat",
            description = "Refreshes the last-heartbeat timestamp for the currently authenticated pharmacist. "
                    + "Pharmacists that fail to send a heartbeat within the configured timeout are automatically "
                    + "flipped to off-duty by the system sweep job.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Heartbeat recorded"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Authentication is required"
            )
    })
    public ResponseEntity<ApiResponse<PresenceStatusResponse>> heartbeat() {
        Long pharmacistId = currentPharmacist.get().getId();
        return ResponseEntity.ok(ApiResponse.success("Heartbeat", presenceService.heartbeat(pharmacistId)));
    }
}
