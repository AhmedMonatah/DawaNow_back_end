package com.example.dawanow.mapper;

import com.example.dawanow.dtos.response.MedicineRequestItemResponse;
import com.example.dawanow.dtos.response.MedicineRequestResponse;
import com.example.dawanow.entity.Customer;
import com.example.dawanow.entity.MedicineRequest;
import com.example.dawanow.entity.RequestItem;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring", uses = ProductMapper.class)
public interface MedicineRequestMapper {

    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "customerName", source = "customer", qualifiedByName = "fullName")
    @Mapping(target = "customerPhone", source = "customer.phoneNumber")
    MedicineRequestResponse toResponse(MedicineRequest request);

    /**
     * Read path: items are supplied explicitly so the lazy `request.items`
     * collection is never touched. MedicineRequestService resolves the
     * localized products before calling this.
     */
    @Mapping(target = "customerId", source = "request.customer.id")
    @Mapping(target = "customerName", source = "request.customer", qualifiedByName = "fullName")
    @Mapping(target = "customerPhone", source = "request.customer.phoneNumber")
    @Mapping(target = "items", source = "items")
    MedicineRequestResponse toResponse(MedicineRequest request, List<MedicineRequestItemResponse> items);

    @Mapping(target = "requestId", source = "item.request.id")
    @Mapping(target = "productId", source = "item.product.id")
    @Mapping(target = "product", source = "item.product")
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
