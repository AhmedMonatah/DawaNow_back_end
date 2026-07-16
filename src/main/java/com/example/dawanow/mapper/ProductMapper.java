package com.example.dawanow.mapper;

import com.example.dawanow.dtos.response.ProductResponse;
import com.example.dawanow.entity.Product;
import com.example.dawanow.entity.ProductTranslation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    ProductResponse toResponse(Product product);

    @Mapping(target = "id", source = "product.id")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "scientificName", source = "scientificName")
    @Mapping(target = "price", source = "product.price")
    @Mapping(target = "imageUrl", source = "product.imageUrl")
    @Mapping(target = "categoryId", source = "product.category.id")
    @Mapping(target = "categoryName", source = "categoryName")
    @Mapping(target = "company", source = "company")
    @Mapping(target = "route", source = "route")
    ProductResponse toResponse(ProductTranslation translation);
}
