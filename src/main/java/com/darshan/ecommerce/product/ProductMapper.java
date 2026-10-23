package com.darshan.ecommerce.product;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
public interface ProductMapper {

    ProductResponse toResponse(Product product);

    // The category is resolved by the service from categoryId; version is checked there, not copied.
    @Mapping(target = "category", ignore = true)
    void applyUpdate(UpdateProductRequest request, @MappingTarget Product product);
}
