package com.empresa.serpent.transactions.web.mapper;

import com.empresa.serpent.shared.mapper.MapStructConfig;
import com.empresa.serpent.transactions.domain.entity.TransactionDetailEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
import com.empresa.serpent.transactions.web.dto.response.TransactionDetailResponse;
import com.empresa.serpent.transactions.web.dto.response.TransactionItemResponse;
import com.empresa.serpent.transactions.web.dto.response.TransactionListResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface TransactionMapper {

    TransactionListResponse toListResponse(TransactionEntity entity);

    @Mapping(target = "paymentMethodId", source = "paymentMethod.id")
    @Mapping(target = "paymentMethodName", source = "paymentMethod.name")
    @Mapping(target = "createdByUserId", source = "createdByUserEntity.id")
    @Mapping(target = "createdByUsername", source = "createdByUserEntity.username")
    @Mapping(target = "saleId", source = "sale.id")
    @Mapping(target = "warehouseId", source = "sale.warehouse.id")
    @Mapping(target = "warehouseName", source = "sale.warehouse.name")
    TransactionDetailResponse toDetailResponse(TransactionEntity entity);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    TransactionItemResponse toItemResponse(TransactionDetailEntity detail);

    List<TransactionItemResponse> toItemResponseList(List<TransactionDetailEntity> details);
}