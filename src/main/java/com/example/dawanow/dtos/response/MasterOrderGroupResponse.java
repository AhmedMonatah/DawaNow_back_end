package com.example.dawanow.dtos.response;

import java.util.List;

public record MasterOrderGroupResponse(Long requestId,
                                       List<MasterOrderResponse> orders) {
}
