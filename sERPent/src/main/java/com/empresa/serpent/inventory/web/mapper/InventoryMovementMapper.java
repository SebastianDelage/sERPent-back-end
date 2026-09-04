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
    /*
      El tipo de transacción y el nombre del depósito de la contraparte salen de las FK que
      ya existían: no hizo falta guardarlos. Lo único que sí se agregó a la tabla fue el ID
      de la contraparte, que no se podía derivar de la fila sola.

      MapStruct genera la navegación con chequeo de null, así que un movimiento sin
      transacción —una carga inicial de stock— llega con transactionType en null y la
      pantalla lo trata como tal.
    */
    @Mapping(target = "transactionType", source = "transaction.type")
    @Mapping(target = "counterpartWarehouseId", source = "counterpartWarehouse.id")
    @Mapping(target = "counterpartWarehouseName", source = "counterpartWarehouse.name")
    InventoryMovementResponse toResponse(InventoryMovementEntity entity);
}
