package com.empresa.serpent.transactions.web.mapper;

import com.empresa.serpent.shared.mapper.MapStructConfig;
import com.empresa.serpent.transactions.domain.entity.ProductPaymentAdjustmentEntity;
import com.empresa.serpent.transactions.web.dto.response.ProductPaymentAdjustmentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface ProductPaymentAdjustmentMapper {

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "paymentMethodId", source = "paymentMethod.id")
    @Mapping(target = "paymentMethodName", source = "paymentMethod.name")
    ProductPaymentAdjustmentResponse toResponse(ProductPaymentAdjustmentEntity entity);

    List<ProductPaymentAdjustmentResponse> toResponseList(List<ProductPaymentAdjustmentEntity> entities);
}
