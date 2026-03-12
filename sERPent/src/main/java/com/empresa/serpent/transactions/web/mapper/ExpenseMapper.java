package com.empresa.serpent.transactions.web.mapper;



import com.empresa.serpent.shared.mapper.MapStructConfig;
import com.empresa.serpent.transactions.domain.entity.ExpenseEntity;
import com.empresa.serpent.transactions.web.dto.response.ExpenseResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface ExpenseMapper {

    @Mapping(target = "transactionId", source = "transaction.id")
    @Mapping(target = "transactionDate", source = "transaction.date")
    @Mapping(target = "total", source = "transaction.total")
    @Mapping(target = "description", source = "transaction.description")

    @Mapping(target = "supplierId", source = "supplier.id")
    @Mapping(target = "supplierName", source = "supplier.name")

    @Mapping(target = "expenseCategoryId", source = "expenseCategory.id")
    @Mapping(target = "expenseCategoryName", source = "expenseCategory.name")

    ExpenseResponse toResponse(ExpenseEntity entity);
}