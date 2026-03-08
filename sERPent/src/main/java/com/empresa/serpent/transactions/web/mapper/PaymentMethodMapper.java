package com.empresa.serpent.transactions.web.mapper;

import com.empresa.serpent.shared.mapper.MapStructConfig;
import com.empresa.serpent.transactions.domain.entity.PaymentMethodEntity;
import com.empresa.serpent.transactions.web.dto.request.CreatePaymentMethodRequest;
import com.empresa.serpent.transactions.web.dto.request.UpdatePaymentMethodRequest;
import com.empresa.serpent.transactions.web.dto.response.PaymentMethodResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MapStructConfig.class)
public interface PaymentMethodMapper {

    @Mapping(target = "id", ignore = true)
    PaymentMethodEntity toEntity(CreatePaymentMethodRequest request);

    PaymentMethodResponse toResponse(PaymentMethodEntity entity);

    @Mapping(target = "id", ignore = true)
    void updateEntityFromRequest(UpdatePaymentMethodRequest request, @MappingTarget PaymentMethodEntity entity);
}