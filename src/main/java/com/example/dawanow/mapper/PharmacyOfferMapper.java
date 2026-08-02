package com.example.dawanow.mapper;

import com.example.dawanow.dtos.response.PharmacyOfferItemResponse;
import com.example.dawanow.dtos.response.PharmacyOfferResponse;
import com.example.dawanow.entity.PharmacyOffer;
import com.example.dawanow.entity.PharmacyOfferItem;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = ProductMapper.class)
public interface PharmacyOfferMapper {
    @Mapping(target = "requestId", source = "request.id")
    @Mapping(target = "pharmacyId", source = "pharmacy.id")
    @Mapping(target = "pharmacistId", source = "pharmacist.id")
    PharmacyOfferResponse toResponse(PharmacyOffer offer);

    /**
     * Read path: items are supplied explicitly so the lazy `offer.items`
     * collection is never touched. PharmacyOfferService resolves the localized
     * products before calling this.
     */
    @Mapping(target = "requestId", source = "offer.request.id")
    @Mapping(target = "pharmacyId", source = "offer.pharmacy.id")
    @Mapping(target = "pharmacistId", source = "offer.pharmacist.id")
    @Mapping(target = "items", source = "items")
    PharmacyOfferResponse toResponse(PharmacyOffer offer, List<PharmacyOfferItemResponse> items);

    @Mapping(target = "offerId", source = "offer.id")
    @Mapping(target = "requestItemId", source = "requestItem.id")
    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "product", source = "product")
    PharmacyOfferItemResponse toItemResponse(PharmacyOfferItem item);
}
