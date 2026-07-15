package com.example.dawanow.mapper;

import com.example.dawanow.dtos.response.PharmacyInvitationResponse;
import com.example.dawanow.entity.PharmacyInvitation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PharmacyInvitationMapper {

    @Mapping(target = "pharmacyName", source = "pharmacy.name")
    PharmacyInvitationResponse toResponse(PharmacyInvitation invitation);
}
