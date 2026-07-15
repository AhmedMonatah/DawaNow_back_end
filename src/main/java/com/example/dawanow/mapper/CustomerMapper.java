package com.example.dawanow.mapper;

import com.example.dawanow.dtos.response.CustomerResponse;
import com.example.dawanow.entity.Customer;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

    CustomerResponse toResponse(Customer customer);
}
