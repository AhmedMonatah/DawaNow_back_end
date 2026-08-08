package com.example.dawanow.dtos.request;

import jakarta.validation.Valid;
import com.example.dawanow.entity.FulfillmentMethod;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ConfirmSelectionRequest(
        @Valid @NotEmpty List<SelectedItem> selectedItems,
    FulfillmentMethod fulfillmentMethod

) {

    public record SelectedItem(
            @NotNull Long requestItemId,
            @NotNull Long productId
    ) {
    }
}