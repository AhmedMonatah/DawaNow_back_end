package com.example.dawanow.mapper;

import com.example.dawanow.dtos.response.CartItemResponse;
import com.example.dawanow.dtos.response.CartResponse;
import com.example.dawanow.entity.Cart;
import com.example.dawanow.entity.CartItem;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = ProductMapper.class)
public interface CartMapper {

    @Mapping(target = "cartId", source = "cart.id")
    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "product", source = "product")
    @Mapping(target = "unitPrice", source = "product.price")
    CartItemResponse toItemResponse(CartItem cartItem);

    CartResponse toResponse(Cart cart);

    /**
     * Read path: items are supplied explicitly so the lazy `cart.items`
     * collection is never touched. CartService resolves the localized
     * products before calling this.
     */
    @Mapping(target = "items", source = "items")
    CartResponse toResponse(Cart cart, List<CartItemResponse> items);
}
