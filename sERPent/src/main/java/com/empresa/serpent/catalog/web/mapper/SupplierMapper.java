package com.empresa.serpent.catalog.web.mapper;

import com.empresa.serpent.catalog.domain.SupplierEntity;
import com.empresa.serpent.catalog.web.dto.request.SupplierCreateRequest;
import com.empresa.serpent.catalog.web.dto.request.SupplierUpdateRequest;
import com.empresa.serpent.catalog.web.dto.response.SupplierResponse;
import com.empresa.serpent.shared.mapper.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MapStructConfig.class)
public interface SupplierMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "address", ignore = true)
    @Mapping(target = "notes", ignore = true)
    SupplierEntity toEntity(SupplierCreateRequest request);

    SupplierResponse toResponse(SupplierEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "address", ignore = true)
    @Mapping(target = "notes", ignore = true)
    void updateEntityFromRequest(SupplierUpdateRequest request, @MappingTarget SupplierEntity entity);
}