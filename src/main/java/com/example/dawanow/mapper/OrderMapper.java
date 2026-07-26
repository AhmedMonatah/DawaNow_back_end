package com.example.dawanow.mapper;

import com.example.dawanow.dtos.response.OrderItemResponse;
import com.example.dawanow.dtos.response.OrderResponse;
import com.example.dawanow.entity.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "customerId", source = "user.id")
    @Mapping(target = "pharmacyId", source = "pharmacy.id")
    @Mapping(target = "pharmacistId", source = "pharmacist.id")
    @Mapping(target = "offerId", source = "offer.id")
    @Mapping(target="createdAt", source="date")
    @Mapping(target = "pharmacyName", source = "pharmacy.name")
    @Mapping(target = "pharmacistName", source = "pharmacist", qualifiedByName = "fullName")
    @Mapping(target = "pharmacyAddress", source = "pharmacy.address")
    @Mapping(target = "pharmacyPhone", source = "pharmacy.phoneNumber")
    @Mapping(target = "customerName", source = "user", qualifiedByName = "fullName")
    @Mapping(target= "subTotal", source = "totalPrice")
    @Mapping(target = "total", expression = "java(order.getTotalPrice().add(order.getDeliveryFee()))")
    @Mapping(target = "deliveryFee", expression = "java(order.getDeliveryFee())")
    OrderResponse toResponse(Order order);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "imageUrl", source = "product.imageUrl")
    @Mapping(target = "totalPrice", expression = "java(orderItem.getUnitPrice().multiply(java.math.BigDecimal.valueOf(orderItem.getQuantity())))")
    OrderItemResponse toResponse(OrderItem orderItem);

    @Named("fullName")
    default String fullName(User user) {
        if (user == null) {
            return null;
        }
        return user.getFirstName() + " " + user.getLastName();
    }
}
