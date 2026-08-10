package com.example.dawanow.dtos.request;

import com.example.dawanow.entity.FulfillmentMethod;

public record FulfillmentRequest(
        FulfillmentMethod fulfillmentMethod
) {
}
