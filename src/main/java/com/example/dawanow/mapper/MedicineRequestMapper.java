package com.example.dawanow.mapper;

import com.example.dawanow.dtos.response.MedicineRequestItemResponse;
import com.example.dawanow.dtos.response.MedicineRequestResponse;
import com.example.dawanow.entity.Customer;
import com.example.dawanow.entity.MedicineRequest;
import com.example.dawanow.entity.RequestItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface MedicineRequestMapper {

    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "customerName", source = "customer", qualifiedByName = "fullName")
    MedicineRequestResponse toResponse(MedicineRequest request);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "imageUrl", source = "item.product.imageUrl")
    @Mapping(target = "productName", source = "item.product.name")
    @Mapping(target = "quantity", source = "item.quantity")
    @Mapping(target = "unitPrice", source = "item.product.price")
    MedicineRequestItemResponse toResponse(RequestItem item);

    @Named("fullName")
    default String fullName(Customer customer) {
        if (customer == null) {
            return null;
        }
        return customer.getFirstName() + " " + customer.getLastName();
    }
}
