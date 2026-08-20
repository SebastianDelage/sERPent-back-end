package com.empresa.serpent.support;

import com.empresa.serpent.transactions.web.dto.request.CreateSaleItemRequest;
import com.empresa.serpent.transactions.web.dto.request.CreateSaleRequest;

import java.math.BigDecimal;
import java.util.List;

public final class TestRequestFactory {

    private TestRequestFactory() {
    }

    public static CreateSaleRequest createSaleRequestOneItem() {
        return new CreateSaleRequest(
                null,
                "Consumidor Final",
                "12345678",
                "A-0001-00000001",
                1L,
                1L,
                1L,
                "Venta mostrador",
                List.of(
                        new CreateSaleItemRequest(
                                10L,
                                null,
                                new BigDecimal("1.000"),
                                new BigDecimal("4500.0000")
                        )
                )
        );
    }
}