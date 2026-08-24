package com.empresa.serpent.catalog.web.dto.response;

/** One supplier a product can be bought from. */
public record ProductSupplierResponse(
        Long id,
        Long productId,
        Long supplierId,
        String supplierName,
        String supplierProductCode,
        Boolean preferred,
        Integer leadTimeDays,
        Boolean active
) {
}
