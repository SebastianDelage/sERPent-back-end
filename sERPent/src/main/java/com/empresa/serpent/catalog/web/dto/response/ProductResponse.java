package com.empresa.serpent.catalog.web.dto.response;

import com.empresa.serpent.catalog.domain.enums.UnitOfMeasure;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String sku,
        String barcode,
        String scaleCode,
        Boolean active,
        BigDecimal minimumStock,
        BigDecimal reorderPoint,
        BigDecimal reorderQuantity,
        LocalDateTime createdAt,
        UnitOfMeasure unitOfMeasure,

        /**
         * Lo que se configuro para este producto, o null si nunca se configuro.
         *
         * <p>Va crudo porque la pantalla de edicion tiene que poder distinguir "usa el valor por
         * omision" de "alguien puso justo ese numero".
         */
        BigDecimal priceAlertPercent,

        /**
         * El que de verdad se aplica. Nunca null.
         *
         * <p>VIAJAN LOS DOS Y NO UNO, para que la regla viva en un solo lado. Si la pantalla
         * tuviera que resolver el valor por omision, el numero quedaria escrito tambien en el
         * frontend y habria que acordarse de cambiarlo en dos lugares.
         */
        BigDecimal effectivePriceAlertPercent
) {
}