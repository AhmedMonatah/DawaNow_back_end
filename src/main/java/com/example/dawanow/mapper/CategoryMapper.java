package com.example.dawanow.mapper;

import com.example.dawanow.dtos.response.CategoryResponse;
import com.example.dawanow.entity.Category;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    CategoryResponse toResponse(Category category);
}
