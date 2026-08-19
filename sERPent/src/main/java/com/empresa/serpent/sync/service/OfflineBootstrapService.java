package com.empresa.serpent.sync.service;

import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.inventory.repository.WarehouseRepository;
import com.empresa.serpent.transactions.repository.PaymentMethodRepository;
import com.empresa.serpent.sync.web.dto.response.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OfflineBootstrapService {

    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final PaymentMethodRepository paymentMethodRepository;

    public OfflineBootstrapResponse bootstrap() {

        var products = productRepository.search(null, false)
                .stream()
                .map(p -> new ProductLiteDto(
                        p.getId(),
                        p.getName(),
                        p.getPrice(),
                        p.getSku(),
                        p.getActive(),
                        p.getUnitOfMeasure()
                ))
                .collect(Collectors.toList());

        var warehouses = warehouseRepository.findByActiveTrue()
                .stream()
                .map(w -> new WarehouseLiteDto(
                        w.getId(),
                        w.getName(),
                        w.getActive()
                ))
                .collect(Collectors.toList());

        var paymentMethods = paymentMethodRepository.search(null, false)
                .stream()
                .map(pm -> new PaymentMethodLiteDto(
                        pm.getId(),
                        pm.getName(),
                        pm.getActive()
                ))
                .collect(Collectors.toList());

        return new OfflineBootstrapResponse(
                products,
                warehouses,
                paymentMethods
        );
    }
}