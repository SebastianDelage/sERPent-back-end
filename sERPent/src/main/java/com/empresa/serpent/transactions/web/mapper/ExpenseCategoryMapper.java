package com.empresa.serpent.transactions.web.mapper;


import com.empresa.serpent.shared.mapper.MapStructConfig;
import com.empresa.serpent.transactions.domain.entity.ExpenseCategoryEntity;
import com.empresa.serpent.transactions.web.dto.request.CreateExpenseCategoryRequest;
import com.empresa.serpent.transactions.web.dto.request.UpdateExpenseCategoryRequest;
import com.empresa.serpent.transactions.web.dto.response.ExpenseCategoryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MapStructConfig.class)
public interface ExpenseCategoryMapper {

    @Mapping(target = "id", ignore = true)
    ExpenseCategoryEntity toEntity(CreateExpenseCategoryRequest request);

    ExpenseCategoryResponse toResponse(ExpenseCategoryEntity entity);

    @Mapping(target = "id", ignore = true)
    void updateEntityFromRequest(
            UpdateExpenseCategoryRequest request,
            @MappingTarget ExpenseCategoryEntity entity
    );
}