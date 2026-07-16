package com.example.dawanow.mapper;

import com.example.dawanow.dtos.response.MedicineRequestItemResponse;
import com.example.dawanow.dtos.response.MedicineRequestResponse;
import com.example.dawanow.entity.MedicineRequest;
import com.example.dawanow.entity.RequestItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MedicineRequestMapper {

    @Mapping(target = "customerId", source = "customer.id")
    MedicineRequestResponse toResponse(MedicineRequest request);

    @Mapping(target = "productId", source = "product.id")
    MedicineRequestItemResponse toResponse(RequestItem item);
}
