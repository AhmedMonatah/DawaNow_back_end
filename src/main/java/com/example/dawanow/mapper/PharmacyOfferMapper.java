package com.example.dawanow.mapper;

import com.example.dawanow.dtos.response.PharmacyOfferItemResponse;
import com.example.dawanow.dtos.response.PharmacyOfferResponse;
import com.example.dawanow.entity.PharmacyOffer;
import com.example.dawanow.entity.PharmacyOfferItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PharmacyOfferMapper {
    @Mapping(target = "requestId", source = "request.id")
    @Mapping(target = "pharmacyId", source = "pharmacy.id")
    @Mapping(target = "pharmacistId", source = "pharmacist.id")
    PharmacyOfferResponse toResponse(PharmacyOffer offer);

    @Mapping(target = "requestItemId", source = "requestItem.id")
    @Mapping(target = "productId", source = "product.id")
    PharmacyOfferItemResponse toItemResponse(PharmacyOfferItem item);
}
