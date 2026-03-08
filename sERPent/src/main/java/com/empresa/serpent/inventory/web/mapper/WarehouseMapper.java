package com.empresa.serpent.inventory.web.mapper;

import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.web.dto.request.CreateWarehouseRequest;
import com.empresa.serpent.inventory.web.dto.request.UpdateWarehouseRequest;
import com.empresa.serpent.inventory.web.dto.response.WarehouseResponse;
import com.empresa.serpent.shared.mapper.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MapStructConfig.class)
public interface WarehouseMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    WarehouseEntity toEntity(CreateWarehouseRequest request);

    WarehouseResponse toResponse(WarehouseEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntityFromRequest(UpdateWarehouseRequest request, @MappingTarget WarehouseEntity entity);
}