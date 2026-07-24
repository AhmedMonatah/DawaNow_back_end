package com.example.dawanow.mapper;

import com.example.dawanow.dtos.response.MedicineRequestResultItemResponse;
import com.example.dawanow.entity.PharmacyOfferItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MedicineRequestResultItemMapper {
    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.productName")
    @Mapping(target = "imageUrl", source = "product.imageUrl")
    @Mapping(target = "unitPrice", source = "product.price")
    @Mapping(target = "alternative", source = "alternative")
    @Mapping(target = "available", constant = "true")
    @Mapping(target = "requestItemId", source = "requestItem.id")
    MedicineRequestResultItemResponse toResponse(PharmacyOfferItem pharmacyOfferItem);
}
