package com.example.dawanow.controller;

import com.example.dawanow.dtos.request.CreatePharmacyInvitationRequest;
import com.example.dawanow.dtos.response.ApiResponse;
import com.example.dawanow.dtos.response.PharmacyInvitationResponse;
import com.example.dawanow.service.PharmacyInvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pharmacy-invitations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PHARMACIST')")
@Tag(name = "Pharmacy Invitations", description = "Invite pharmacists to join a pharmacy and manage invitations")
public class PharmacyInvitationController {
    private final PharmacyInvitationService pharmacyInvitationService;

    @GetMapping("/me")
    @Operation(
            summary = "Get my pending invitations",
            description = "Returns all pending invitations for the currently authenticated pharmacist.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Pending invitations fetched"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Pharmacist role is required"
            )
    })
    public ResponseEntity<ApiResponse<List<PharmacyInvitationResponse>>> getMyPendingInvitations() {
        return ResponseEntity.ok(ApiResponse.success("Pending pharmacy invitations fetched", pharmacyInvitationService.getMyPendingInvitations()));
    }

    @PostMapping("/pharmacy/{pharmacyId}")
    @Operation(
            summary = "Invite a pharmacist",
            description = "Pharmacy admin invites another pharmacist to join the pharmacy. "
                    + "The invited pharmacist must not already belong to a pharmacy.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Pharmacist invited successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Pharmacist already assigned, duplicate invitation, or request validation failed"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Only the pharmacy admin can invite"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Pharmacy or pharmacist not found"
            )
    })
    public ResponseEntity<ApiResponse<PharmacyInvitationResponse>> invite(@PathVariable Long pharmacyId,
            @Valid @RequestBody CreatePharmacyInvitationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Pharmacist invited", pharmacyInvitationService.invite(pharmacyId, request)));
    }

    @GetMapping("/admin")
    @Operation(
            summary = "View pending invitations for my pharmacy",
            description = "Pharmacy admin views all pending invitations for their own pharmacy, inferred from the current user.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Pending invitations fetched"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Only the pharmacy admin can view invitations"
            )
    })
    public ResponseEntity<ApiResponse<List<PharmacyInvitationResponse>>> getPendingInvitationsForAdmin() {
        return ResponseEntity.ok(ApiResponse.success("Pending pharmacy invitations fetched",
                pharmacyInvitationService.getPendingInvitationsForMyPharmacy()));
    }

    @GetMapping("/pharmacy/{pharmacyId}")
    @Operation(
            summary = "View pending invitations for a pharmacy",
            description = "Pharmacy admin views all pending invitations for a specific pharmacy.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Pending invitations fetched"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Only the pharmacy admin can view invitations"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Pharmacy not found"
            )
    })
    public ResponseEntity<ApiResponse<List<PharmacyInvitationResponse>>> getPendingInvitationsForPharmacy(
            @Parameter(description = "Pharmacy ID", example = "1", required = true)
            @PathVariable Long pharmacyId
    ) {
        return ResponseEntity.ok(ApiResponse.success("Pending pharmacy invitations fetched",
                pharmacyInvitationService.getPendingInvitationsForPharmacy(pharmacyId)));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a pending invitation",
            description = "Pharmacy admin deletes a pending invitation by its ID. Only pending invitations can be deleted.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Invitation deleted; response data is null"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invitation is no longer pending"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Only the pharmacy admin can delete invitations"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Invitation not found"
            )
    })
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(description = "Invitation ID", example = "1", required = true)
            @PathVariable Long id
    ) {
        pharmacyInvitationService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Pharmacy invitation deleted"));
    }

    @PatchMapping("/{id}/accept")
    @Operation(
            summary = "Accept an invitation",
            description = "Pharmacist accepts a pending invitation and joins the pharmacy. "
                    + "The pharmacist must not already belong to a pharmacy.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Invitation accepted; pharmacist now assigned to the pharmacy"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Already assigned to a pharmacy or invitation is no longer pending"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "This invitation belongs to another pharmacist"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Invitation not found"
            )
    })
    public ResponseEntity<ApiResponse<PharmacyInvitationResponse>> accept(
            @Parameter(description = "Invitation ID", example = "1", required = true)
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success("Pharmacy invitation accepted", pharmacyInvitationService.accept(id)));
    }

    @PatchMapping("/{id}/decline")
    @Operation(
            summary = "Decline an invitation",
            description = "Pharmacist declines a pending invitation.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "Invitation declined"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invitation is no longer pending"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "This invitation belongs to another pharmacist"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Invitation not found"
            )
    })
    public ResponseEntity<ApiResponse<PharmacyInvitationResponse>> decline(
            @Parameter(description = "Invitation ID", example = "1", required = true)
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success("Pharmacy invitation declined", pharmacyInvitationService.decline(id)));
    }
}
