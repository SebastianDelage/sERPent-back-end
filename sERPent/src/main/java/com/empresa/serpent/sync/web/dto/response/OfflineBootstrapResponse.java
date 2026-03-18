package com.empresa.serpent.sync.web.dto.response;

import java.util.List;

public record OfflineBootstrapResponse(

        List<ProductLiteDto> products,
        List<WarehouseLiteDto> warehouses,
        List<PaymentMethodLiteDto> paymentMethods
) {}