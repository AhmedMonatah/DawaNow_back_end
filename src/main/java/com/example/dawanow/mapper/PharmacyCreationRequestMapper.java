package com.example.dawanow.mapper;

import com.example.dawanow.dtos.response.PharmacyCreationRequestResponse;
import com.example.dawanow.entity.PharmacyCreationRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PharmacyCreationRequestMapper {

    @Mapping(target = "pharmacistName", expression = "java(request.getPharmacist().getFirstName() + \" \" + request.getPharmacist().getLastName())")
    @Mapping(target = "reviewedById", source = "reviewedBy.id")
    @Mapping(target = "approvedPharmacyId", source = "approvedPharmacy.id")
    PharmacyCreationRequestResponse toResponse(PharmacyCreationRequest request);
}
