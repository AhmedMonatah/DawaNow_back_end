package com.example.dawanow.dtos.response;

import java.util.List;

/**
 * SSE payload pushed to a customer's stream when a new offer changes the
 * availability status of one or more request items. Carries only the changed
 * items (request item id + status); the client merges them into its state.
 */
public record RequestResultUpdateEvent(
        Long requestId,
        List<RequestItemStatusUpdate> updatedItems
) {
}
