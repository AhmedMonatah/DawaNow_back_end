package com.example.dawanow.controller;

import com.example.dawanow.dtos.response.ApiResponse;
import com.example.dawanow.dtos.response.NotificationResponse;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.entity.notification.NotificationRecipient;
import com.example.dawanow.service.CurrentPharmacistProvider;
import com.example.dawanow.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pharmacists/me/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Notification inbox for the authenticated pharmacist")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentPharmacistProvider currentPharmacist;

    @GetMapping
    @Operation(
            summary = "List notifications",
            description = "Returns a paginated list of notifications for the currently authenticated pharmacist, "
                    + "optionally filtered by status (SENT, READ, etc.).",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Notifications fetched successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Authentication is required"
            )
    })
    public ResponseEntity<ApiResponse<PaginatedResponse<NotificationResponse>>> list(
            @Parameter(description = "Filter by delivery/read status", schema = @Schema(implementation = NotificationRecipient.Status.class))
            @RequestParam(required = false) NotificationRecipient.Status status,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        Long pharmacistId = currentPharmacist.get().getId();
        Page<NotificationResponse> page = notificationService.list(pharmacistId, status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Notifications fetched", PaginatedResponse.from(page)));
    }

    @GetMapping("/unread-count")
    @Operation(
            summary = "Get unread notification count",
            description = "Returns the number of unread notifications — typically used to render a badge on a bell icon.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Unread count returned"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Authentication is required"
            )
    })
    public ResponseEntity<ApiResponse<Long>> unreadCount() {
        Long pharmacistId = currentPharmacist.get().getId();
        return ResponseEntity.ok(ApiResponse.success("Unread count", notificationService.countUnread(pharmacistId)));
    }

    @PatchMapping("/{recipientId}/read")
    @Operation(
            summary = "Mark notification as read",
            description = "Marks a single notification as read by its recipient ID.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Notification marked as read"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Authentication is required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Notification recipient not found"
            )
    })
    public ResponseEntity<ApiResponse<Void>> markRead(
            @Parameter(description = "Recipient ID", example = "1", required = true)
            @PathVariable Long recipientId) {
        Long pharmacistId = currentPharmacist.get().getId();
        notificationService.markRead(pharmacistId, recipientId);
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read"));
    }

    @PatchMapping("/read-all")
    @Operation(
            summary = "Mark all notifications as read",
            description = "Marks every unread notification for the currently authenticated pharmacist as read.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "All notifications marked as read"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Authentication is required"
            )
    })
    public ResponseEntity<ApiResponse<Void>> markAllRead() {
        Long pharmacistId = currentPharmacist.get().getId();
        notificationService.markAllRead(pharmacistId);
        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read"));
    }
}
