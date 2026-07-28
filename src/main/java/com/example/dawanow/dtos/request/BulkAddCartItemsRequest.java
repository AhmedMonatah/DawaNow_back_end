package com.example.dawanow.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BulkAddCartItemsRequest(
        @NotEmpty(message = "Cart items are required") List<@Valid AddCartItemRequest> items
) {
}
