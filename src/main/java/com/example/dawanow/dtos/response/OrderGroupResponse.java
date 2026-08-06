package com.example.dawanow.dtos.response;

import java.util.List;

public record OrderGroupResponse(
        Long requestId,
        List<OrderResponse> orders
) {
}
