package com.example.dawanow.mapper;

import com.example.dawanow.dtos.response.UserResponse;
import com.example.dawanow.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}
