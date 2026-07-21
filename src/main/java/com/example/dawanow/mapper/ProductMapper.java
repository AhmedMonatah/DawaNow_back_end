package com.example.dawanow.mapper;

import com.example.dawanow.dtos.response.ProductResponse;
import com.example.dawanow.entity.Product;
import com.example.dawanow.entity.ProductTranslation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "consumerCategory", source = "category.name")
    ProductResponse toResponse(Product product);

    @Mapping(target = "id", source = "product.id")
    @Mapping(target = "price", source = "product.price")
    @Mapping(target = "imageUrl", source = "product.imageUrl")
    @Mapping(target = "categoryId", source = "product.category.id")
    ProductResponse toResponse(ProductTranslation translation);
}
