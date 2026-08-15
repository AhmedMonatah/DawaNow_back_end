package com.example.dawanow.controller;

import com.example.dawanow.dtos.request.ChatMessageRequest;
import com.example.dawanow.dtos.response.ApiResponse;
import com.example.dawanow.dtos.response.ChatHistoryResponse;
import com.example.dawanow.dtos.response.ChatMessageResponse;
import com.example.dawanow.service.ai.chat.AiChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ai/chat")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('CUSTOMER','PHARMACIST')")
@Tag(name = "AI Chat", description = "Conversational AI health assistant grounded in the Medsy catalog")
public class AiChatController {

    private final AiChatService aiChatService;

    @PostMapping("/messages")
    @Operation(
            summary = "Send a chat message and receive the assistant's structured reply",
            description = "Continues the caller's own conversation automatically. The message language is "
                    + "detected server-side and product suggestions are capped server-side.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    public ResponseEntity<ApiResponse<ChatMessageResponse>> sendMessage(
            @Valid @RequestBody ChatMessageRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Chat reply generated",
                aiChatService.sendMessage(request)
        ));
    }

    @PostMapping(value = "/messages/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Send a prescription or medicine-box photo to the chat",
            description = "Extracts medicine names from the image, matches them against the catalog and "
                    + "replies conversationally with the matched products.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    public ResponseEntity<ApiResponse<ChatMessageResponse>> sendImageMessage(
            @RequestPart("image") MultipartFile image,
            @RequestParam(required = false) @Size(max = 2000) String message,
            @RequestHeader(value = "X-AI-Api-Key", required = false) String aiApiKey
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Chat reply generated",
                aiChatService.sendImageMessage(message, image, aiApiKey)
        ));
    }

    @GetMapping("/history")
    @Operation(
            summary = "Load the caller's full chat history",
            description = "Call this when the chat screen opens. Returns every stored message with its "
                    + "resolved products, specializations and emergency numbers.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    public ResponseEntity<ApiResponse<ChatHistoryResponse>> getHistory() {
        return ResponseEntity.ok(ApiResponse.success(
                "Chat history fetched",
                aiChatService.getHistory()
        ));
    }

    @DeleteMapping("/history")
    @Operation(
            summary = "Start a new chat by clearing the caller's history",
            description = "Deletes every message while keeping the same conversation id.",
            security = @SecurityRequirement(name = "basicAuth")
    )
    public ResponseEntity<ApiResponse<ChatHistoryResponse>> clearHistory() {
        return ResponseEntity.ok(ApiResponse.success(
                "Chat history cleared",
                aiChatService.clearHistory()
        ));
    }
}
