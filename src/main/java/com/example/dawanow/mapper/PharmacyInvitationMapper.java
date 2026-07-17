package com.example.dawanow.mapper;

import com.example.dawanow.dtos.response.PharmacyInvitationResponse;
import com.example.dawanow.entity.PharmacyInvitation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PharmacyInvitationMapper {

    @Mapping(target = "pharmacyId", source = "pharmacy.id")
    @Mapping(target = "pharmacyName", source = "pharmacy.name")
    @Mapping(target = "pharmacistId", source = "pharmacist.id")
    @Mapping(target = "pharmacistFirstName", source = "pharmacist.firstName")
    @Mapping(target = "pharmacistLastName", source = "pharmacist.lastName")
    PharmacyInvitationResponse toResponse(PharmacyInvitation invitation);
}
