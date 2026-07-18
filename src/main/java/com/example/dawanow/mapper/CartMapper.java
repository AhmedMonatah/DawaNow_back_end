package com.example.dawanow.mapper;

import com.example.dawanow.dtos.response.CartResponse;
import com.example.dawanow.entity.Cart;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CartMapper {

    CartResponse toResponse(Cart cart);
}
