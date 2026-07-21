package com.example.dawanow.mapper;

import com.example.dawanow.dtos.response.CategoryResponse;
import com.example.dawanow.entity.Category;
import com.example.dawanow.entity.CategoryTranslation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    CategoryResponse toResponse(Category category);

    @Mapping(target = "id", source = "category.id")
    CategoryResponse toResponse(CategoryTranslation translation);
}
