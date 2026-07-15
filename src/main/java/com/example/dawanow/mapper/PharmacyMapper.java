package com.example.dawanow.mapper;

import com.example.dawanow.dtos.response.PharmacyResponse;
import com.example.dawanow.entity.Pharmacy;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PharmacyMapper {

    PharmacyResponse toResponse(Pharmacy pharmacy);
}
