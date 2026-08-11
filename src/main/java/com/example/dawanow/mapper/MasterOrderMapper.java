package com.example.dawanow.mapper;

import com.example.dawanow.dtos.response.MasterOrderResponse;
import com.example.dawanow.dtos.response.OrderDraftResponse;
import com.example.dawanow.dtos.response.OrderResponse;
import com.example.dawanow.entity.MasterOrder;
import com.example.dawanow.entity.Order;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MasterOrderMapper {

    @Mapping(target = "requestId", source = "masterOrder.request.id")
    @Mapping(target = "orderResponses", source = "orderResponses")
    MasterOrderResponse toResponse(
            MasterOrder masterOrder,
            List<OrderDraftResponse> orderResponses
    );
}