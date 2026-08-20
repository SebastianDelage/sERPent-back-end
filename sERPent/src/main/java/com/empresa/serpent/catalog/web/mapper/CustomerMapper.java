package com.empresa.serpent.catalog.web.mapper;

import com.empresa.serpent.catalog.domain.entity.CustomerEntity;
import com.empresa.serpent.catalog.web.dto.request.CustomerCreateRequest;
import com.empresa.serpent.catalog.web.dto.request.CustomerUpdateRequest;
import com.empresa.serpent.catalog.web.dto.response.CustomerResponse;
import com.empresa.serpent.shared.mapper.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MapStructConfig.class)
public interface CustomerMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    CustomerEntity toEntity(CustomerCreateRequest request);

    CustomerResponse toResponse(CustomerEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntityFromRequest(CustomerUpdateRequest request, @MappingTarget CustomerEntity entity);
}
