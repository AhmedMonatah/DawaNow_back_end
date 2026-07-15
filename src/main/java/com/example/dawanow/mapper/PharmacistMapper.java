package com.example.dawanow.mapper;

import com.example.dawanow.dtos.response.PharmacistResponse;
import com.example.dawanow.entity.Pharmacist;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PharmacistMapper {

    @Mapping(target = "pharmacyId", source = "pharmacy.id")
    @Mapping(target = "pharmacyAdmin", expression = "java(pharmacist.getPharmacy() != null && pharmacist.getPharmacy().getAdminPharmacist().getId().equals(pharmacist.getId()))")
    PharmacistResponse toResponse(Pharmacist pharmacist);
}
