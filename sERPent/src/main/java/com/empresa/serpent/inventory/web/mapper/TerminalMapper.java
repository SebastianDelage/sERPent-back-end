package com.empresa.serpent.inventory.web.mapper;

import com.empresa.serpent.inventory.domain.entity.TerminalEntity;
import com.empresa.serpent.inventory.web.dto.response.TerminalResponse;
import com.empresa.serpent.shared.mapper.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface TerminalMapper {

    @Mapping(target = "warehouseId", source = "warehouse.id")
    @Mapping(target = "warehouseName", source = "warehouse.name")
    TerminalResponse toResponse(TerminalEntity entity);
}
