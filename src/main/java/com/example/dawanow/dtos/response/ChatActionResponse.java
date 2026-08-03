package com.example.dawanow.dtos.response;

import java.util.List;

/**
 * One-shot instruction for the mobile app attached to a chat reply.
 *
 * <p>{@code ADDED_TO_CART} reports a cart mutation the backend already
 * performed; {@code CREATE_REQUEST} asks the app to navigate to its
 * confirm-request screen (the backend never creates the request itself).
 * Deliberately not persisted with the message: replaying a navigation
 * instruction from history would be wrong.</p>
 */
public record ChatActionResponse(
        String type,
        List<Long> addedProductIds,
        Integer quantity,
        Long cartItemCount
) {

    public static final String TYPE_ADDED_TO_CART = "ADDED_TO_CART";
    public static final String TYPE_CREATE_REQUEST = "CREATE_REQUEST";

    public static ChatActionResponse addedToCart(List<Long> productIds, int quantity, Long cartItemCount) {
        return new ChatActionResponse(TYPE_ADDED_TO_CART, productIds, quantity, cartItemCount);
    }

    public static ChatActionResponse createRequest() {
        return new ChatActionResponse(TYPE_CREATE_REQUEST, null, null, null);
    }
}
