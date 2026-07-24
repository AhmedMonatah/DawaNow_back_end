package com.example.dawanow.mapper;

import com.example.dawanow.dtos.response.MedicineRequestResultItemResponse;
import com.example.dawanow.dtos.response.MedicineRequestResultResponse;
import com.example.dawanow.entity.MedicineRequest;
import org.mapstruct.Mapper;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface MedicineRequestResultMapper {
    MedicineRequestResultResponse toResponse(MedicineRequest medicineRequest);
    public static MedicineRequestResultItemResponse unavailable(String requestedProductName, Long requestItemId) {
        return new MedicineRequestResultItemResponse(
                requestItemId,
                null,
                requestedProductName,
                null,
                BigDecimal.ZERO,
                false,  // alternative
                false   // available
        );
    }
}
