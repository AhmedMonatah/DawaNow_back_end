package com.example.dawanow.mapper;

import com.example.dawanow.dtos.response.OrderItemResponse;
import com.example.dawanow.dtos.response.OrderResponse;
import com.example.dawanow.entity.*;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring", uses = ProductMapper.class)
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
    @Mapping(target = "total", source = "totalPrice")
    @Mapping(target = "customerNotes", source="request.notes")
    @Mapping(target = "deliveryAddress", source="request.deliveryAddress")
    @Mapping(target = "phoneNumber", source="user.phoneNumber")
    @Mapping(target = "prescriptionUrl", source="request.prescriptionUrl")
    @Mapping(target = "paymentMethod", source = "masterOrder.paymentMethod")
    OrderResponse toResponse(Order order);

    /**
     * Read path: items are supplied explicitly so the lazy `order.items`
     * collection is never touched. OrderService resolves the localized
     * products before calling this.
     */
    @Mapping(target = "customerId", source = "order.user.id")
    @Mapping(target = "pharmacyId", source = "order.pharmacy.id")
    @Mapping(target = "pharmacistId", source = "order.pharmacist.id")
    @Mapping(target = "offerId", source = "order.offer.id")
    @Mapping(target="createdAt", source="order.date")
    @Mapping(target = "pharmacyName", source = "order.pharmacy.name")
    @Mapping(target = "pharmacistName", source = "order.pharmacist", qualifiedByName = "fullName")
    @Mapping(target = "pharmacyAddress", source = "order.pharmacy.address")
    @Mapping(target = "pharmacyPhone", source = "order.pharmacy.phoneNumber")
    @Mapping(target = "customerName", source = "order.user", qualifiedByName = "fullName")
    @Mapping(target= "subTotal", source = "order.totalPrice")
    @Mapping(target = "total", source = "order.totalPrice")
    @Mapping(target = "customerNotes", source="order.request.notes")
    @Mapping(target = "deliveryAddress", source="order.request.deliveryAddress")
    @Mapping(target = "phoneNumber", source="order.user.phoneNumber")
    @Mapping(target = "prescriptionUrl", source="order.request.prescriptionUrl")
    @Mapping(target = "paymentMethod", source = "order.masterOrder.paymentMethod")
    @Mapping(target = "items", source = "orderItems")
    OrderResponse toResponse(Order order, List<OrderItemResponse> orderItems);

    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "product", source = "product")
    OrderItemResponse toResponse(OrderItem orderItem);

    @Named("fullName")
    default String fullName(User user) {
        if (user == null) {
            return null;
        }
        return user.getFirstName() + " " + user.getLastName();
    }
}
