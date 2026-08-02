package com.example.dawanow.mapper;

import com.example.dawanow.dtos.response.MedicineRequestResultItemResponse;
import com.example.dawanow.dtos.response.MedicineRequestResultResponse;
import com.example.dawanow.entity.MedicineRequest;
import org.mapstruct.Mapper;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface MedicineRequestResultMapper {
    MedicineRequestResultResponse toResponse(MedicineRequest medicineRequest);
    static MedicineRequestResultItemResponse unavailable(Long requestItemId) {
        MedicineRequestResultItemResponse response = new MedicineRequestResultItemResponse();
        response.setRequestItemId(requestItemId);
        response.setUnitPrice(BigDecimal.ZERO);
        response.setAlternative(false);
        response.setAvailable(false);
        return response;
    }
}
