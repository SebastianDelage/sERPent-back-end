package com.empresa.serpent.inventory.web.mapper;

import com.empresa.serpent.inventory.domain.entity.InventoryMovementEntity;
import com.empresa.serpent.inventory.web.dto.response.InventoryMovementResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InventoryMovementMapper {

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "warehouseId", source = "warehouse.id")
    @Mapping(target = "warehouseName", source = "warehouse.name")
    @Mapping(target = "transactionId", source = "transaction.id")
    InventoryMovementResponse toResponse(InventoryMovementEntity entity);
}
