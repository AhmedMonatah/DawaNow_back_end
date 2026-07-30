package com.example.dawanow.mapper;

import com.example.dawanow.dtos.response.CartItemResponse;
import com.example.dawanow.dtos.response.CartResponse;
import com.example.dawanow.entity.Cart;
import com.example.dawanow.entity.CartItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = ProductMapper.class)
public interface CartMapper {

    @Mapping(target = "product", source = "product")
    @Mapping(target = "unitPrice", source = "product.price")
    @Mapping(target = "subtotal",
            expression = "java(cartItem.getUnitPrice().multiply(java.math.BigDecimal.valueOf(cartItem.getQuantity())))")
    CartItemResponse toItemResponse(CartItem cartItem);
    CartResponse toResponse(Cart cart);
}
