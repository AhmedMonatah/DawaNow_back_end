package com.example.dawanow.dtos.response;

import com.example.dawanow.entity.RequestItemStatus;

/**
 * Minimal SSE delta for a single request item: its id, its new availability
 * status (FOUND, ALTERNATIVE_FOUND or NOT_FOUND) and, only when the status is
 * ALTERNATIVE_FOUND, the localized summary of the alternative product that was
 * found (null for every other status).
 */
public record RequestItemStatusUpdate(Long requestItemId, RequestItemStatus status, ProductSummaryResponse product) {
}
