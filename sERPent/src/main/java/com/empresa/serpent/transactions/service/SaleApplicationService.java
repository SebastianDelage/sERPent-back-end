package com.empresa.serpent.transactions.service;

import com.empresa.serpent.catalog.domain.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.inventory.domain.entity.InventoryMovementEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.domain.enums.MovementType;
import com.empresa.serpent.inventory.domain.repository.InventoryMovementRepository;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.transactions.domain.entity.*;
import com.empresa.serpent.transactions.domain.enums.TransactionStatus;
import com.empresa.serpent.transactions.domain.enums.TransactionType;
import com.empresa.serpent.transactions.repository.PaymentMethodRepository;
import com.empresa.serpent.transactions.repository.SaleRepository;
import com.empresa.serpent.transactions.repository.TransactionRepository;
import com.empresa.serpent.transactions.repository.UserRepository;
import com.empresa.serpent.transactions.web.dto.request.CreateSaleItemRequest;
import com.empresa.serpent.transactions.web.dto.request.CreateSaleRequest;
import com.empresa.serpent.transactions.web.dto.response.CreateSaleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SaleApplicationService {

    private final TransactionRepository transactionRepository;
    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final InventoryMovementRepository inventoryMovementRepository;

    @Transactional
    public CreateSaleResponse createSale(CreateSaleRequest request) {

        /*
         TODO FUTURO:
         Validar stock disponible antes de permitir la venta.
         Ejemplo: inventoryService.validateStock(productId, quantity)
         */

        UserEntity createdBy = userRepository.findById(request.createdByUserId())
                .orElseThrow(() ->
                        new NotFoundException("User not found: " + request.createdByUserId()));

        PaymentMethodEntity paymentMethod = null;

        if (request.paymentMethodId() != null) {
            paymentMethod = paymentMethodRepository.findById(request.paymentMethodId())
                    .orElseThrow(() ->
                            new NotFoundException("Payment method not found: " + request.paymentMethodId()));
        }

        /*
         TODO FUTURO:
         Resolver el warehouse real.
         Opciones posibles:
         - warehouse por request
         - warehouse por sucursal del usuario
         - warehouse default del sistema
         */

        WarehouseEntity warehouse = WarehouseEntity.builder()
                .id(1L) // TEMPORAL
                .build();

        /*
         Cargamos productos en batch para evitar N+1 queries
         */
        List<Long> productIds = request.items()
                .stream()
                .map(CreateSaleItemRequest::productId)
                .toList();

        List<ProductEntity> products = productRepository.findByIdIn(productIds);

        Map<Long, ProductEntity> productMap = products.stream()
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));

        /*
         Creamos Transaction
         */
        TransactionEntity transaction = TransactionEntity.builder()
                .type(TransactionType.SALE)
                .status(TransactionStatus.CONFIRMED) // Venta real impacta stock
                .description(request.description())
                .paymentMethod(paymentMethod)
                .createdByUserEntity(createdBy)
                .total(BigDecimal.ZERO)
                .details(new ArrayList<>())
                .build();

        BigDecimal total = BigDecimal.ZERO;

        for (CreateSaleItemRequest item : request.items()) {

            ProductEntity product = productMap.get(item.productId());

            if (product == null) {
                throw new NotFoundException("Product not found: " + item.productId());
            }

            BigDecimal subtotal = item.unitPrice()
                    .multiply(item.quantity());

            TransactionDetailEntity detail = TransactionDetailEntity.builder()
                    .transaction(transaction)
                    .product(product)
                    .description(
                            item.description() != null
                                    ? item.description()
                                    : product.getName()
                    )
                    .quantity(item.quantity())
                    .unitPrice(item.unitPrice())
                    .subtotal(subtotal)
                    .build();

            transaction.getDetails().add(detail);

            total = total.add(subtotal);
        }

        transaction.setTotal(total);

        TransactionEntity savedTransaction = transactionRepository.save(transaction);

        /*
         Creamos la entidad Sale
         */

        SaleEntity sale = SaleEntity.builder()
                .transaction(savedTransaction)
                .customerId(request.customerId())
                .customerName(request.customerName())
                .customerDocument(request.customerDocument())
                .invoiceNumber(request.invoiceNumber())
                .taxTotal(BigDecimal.ZERO)
                .build();

        SaleEntity savedSale = saleRepository.save(sale);

        /*
         Generamos movimientos de inventario
         */

        List<InventoryMovementEntity> movements = savedTransaction
                .getDetails()
                .stream()
                .map(detail ->
                        InventoryMovementEntity.builder()
                                .product(detail.getProduct())
                                .warehouseEntity(warehouse)
                                .transaction(savedTransaction)
                                .movementType(MovementType.OUT)
                                .quantity(detail.getQuantity())
                                .unitCost(null)
                                .note("Sale #" + savedTransaction.getId())
                                .build()
                )
                .toList();

        inventoryMovementRepository.saveAll(movements);

        /*
         TODO FUTURO:
         - calcular impuestos
         - integración AFIP
         - generar CAE
         - validar duplicados de invoiceNumber
         */

        return new CreateSaleResponse(
                savedTransaction.getId(),
                savedSale.getId(),
                savedTransaction.getStatus().name(),
                "Sale created successfully"
        );
    }
}
