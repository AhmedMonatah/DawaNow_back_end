package com.example.dawanow.dtos.request;

import com.example.dawanow.entity.FulfillmentMethod;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ConfirmSelectionRequest(
        @NotEmpty List<Long> selectedRequestItemIds,
        FulfillmentMethod fulfillmentMethod
){}
