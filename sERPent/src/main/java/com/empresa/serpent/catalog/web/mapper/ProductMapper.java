package com.empresa.serpent.catalog.web.mapper;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.web.dto.request.ProductCreateRequest;
import com.empresa.serpent.catalog.web.dto.request.ProductUpdateRequest;
import com.empresa.serpent.catalog.web.dto.response.ProductResponse;
import com.empresa.serpent.shared.mapper.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MapStructConfig.class)
public interface ProductMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    ProductEntity toEntity(ProductCreateRequest request);

    // El efectivo se resuelve ACA y no en la pantalla: el valor por omision es una regla del
    // dominio, y escribirlo tambien en el frontend seria tener que acordarse de cambiarlo en dos
    // lugares el dia que se mueva.
    @Mapping(
            target = "effectivePriceAlertPercent",
            expression = "java(com.empresa.serpent.catalog.domain.PriceAlertPolicy"
                    + ".effectivePercent(entity.getPriceAlertPercent()))"
    )
    ProductResponse toResponse(ProductEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntityFromRequest(ProductUpdateRequest request, @MappingTarget ProductEntity entity);
}