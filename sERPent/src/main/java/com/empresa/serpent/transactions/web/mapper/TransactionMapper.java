package com.empresa.serpent.transactions.web.mapper;

import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
import com.empresa.serpent.transactions.web.dto.response.TransactionDetailResponse;
import com.empresa.serpent.transactions.web.dto.response.TransactionListResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface TransactionMapper {

    TransactionListResponse toListResponse(TransactionEntity entity);

    @Mapping(target = "paymentMethodId", source = "paymentMethod.id")
    @Mapping(target = "paymentMethodName", source = "paymentMethod.name")
    @Mapping(target = "createdByUserId", source = "createdByUserEntity.id")
    @Mapping(target = "createdByUsername", source = "createdByUserEntity.username")
    TransactionDetailResponse toDetailResponse(TransactionEntity entity);
}
