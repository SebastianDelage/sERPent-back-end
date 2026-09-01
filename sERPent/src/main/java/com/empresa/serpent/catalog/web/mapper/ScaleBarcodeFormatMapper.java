package com.empresa.serpent.catalog.web.mapper;

import com.empresa.serpent.catalog.domain.entity.ScaleBarcodeFormatEntity;
import com.empresa.serpent.catalog.web.dto.response.ScaleBarcodeFormatResponse;
import com.empresa.serpent.shared.mapper.MapStructConfig;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface ScaleBarcodeFormatMapper {

    ScaleBarcodeFormatResponse toResponse(ScaleBarcodeFormatEntity entity);
}
