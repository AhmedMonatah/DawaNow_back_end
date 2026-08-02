package com.example.dawanow.mapper;

import com.example.dawanow.dtos.response.PharmacyOfferItemResponse;
import com.example.dawanow.dtos.response.PharmacyOfferResponse;
import com.example.dawanow.entity.PharmacyOffer;
import com.example.dawanow.entity.PharmacyOfferItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = ProductMapper.class)
public interface PharmacyOfferMapper {
    @Mapping(target = "requestId", source = "request.id")
    @Mapping(target = "pharmacyId", source = "pharmacy.id")
    @Mapping(target = "pharmacistId", source = "pharmacist.id")
    PharmacyOfferResponse toResponse(PharmacyOffer offer);

    @Mapping(target = "requestItemId", source = "requestItem.id")
    @Mapping(target = "product", source = "product")
    PharmacyOfferItemResponse toItemResponse(PharmacyOfferItem item);
}
