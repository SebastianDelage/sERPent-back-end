package com.empresa.serpent.inventory.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.domain.enums.MovementType;
import com.empresa.serpent.inventory.web.dto.request.CreateInventoryAdjustmentRequest;
import com.empresa.serpent.inventory.web.dto.response.CreateInventoryAdjustmentResponse;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.exception.ValidationException;
import com.empresa.serpent.transactions.domain.entity.TransactionDetailEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
import com.empresa.serpent.transactions.domain.enums.TransactionStatus;
import com.empresa.serpent.transactions.domain.enums.TransactionType;
import com.empresa.serpent.transactions.repository.TransactionRepository;
import com.empresa.serpent.shared.security.AuthenticatedUserService;
import com.empresa.serpent.users.domain.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class InventoryAdjustmentApplicationService {

    private final TransactionRepository transactionRepository;
    private final ProductRepository productRepository;
    private final AuthenticatedUserService authenticatedUserService;
    private final WarehouseAccessService warehouseAccessService;
    private final StockQueryService stockQueryService;
    private final InventoryMovementService inventoryMovementService;

    @Transactional
    public CreateInventoryAdjustmentResponse createAdjustment(CreateInventoryAdjustmentRequest request) {

        UserEntity createdBy = authenticatedUserService.requireCurrentUser();
        authenticatedUserService.requireMatchingCreatedByUserId(request.createdByUserId(), createdBy);

        ProductEntity product = productRepository.findById(request.productId())
                .orElseThrow(() ->
                        new NotFoundException("Product not found: " + request.productId()));

        // Resolves the warehouse (from the terminal when one is named), and checks that it
        // exists, is active, and is assigned to the acting user.
        WarehouseEntity warehouse = warehouseAccessService.resolveForOperation(
                request.terminalId(), request.warehouseId(), createdBy);

        BigDecimal previousStock = stockQueryService.getStockByProductAndWarehouse(
                request.productId(),
                warehouse.getId()
        );

        if (request.countedQuantity().compareTo(previousStock) == 0) {
            throw new ValidationException("La cantidad contada coincide con el stock actual. No hace falta registrar un ajuste.");
        }

        MovementType movementType = request.countedQuantity().compareTo(previousStock) > 0
                ? MovementType.ADJUSTMENT_IN
                : MovementType.ADJUSTMENT_OUT;

        BigDecimal adjustmentQuantity = request.countedQuantity()
                .subtract(previousStock)
                .abs();

        /*
         * ONLY WHAT THE PERSON WROTE. No fallback sentence.
         *
         * <p>This used to fall back to "Inventory adjustment for product 5 in warehouse 2":
         * English, ids instead of names, and frozen in the database the moment it was saved.
         * The screen showed it under "Descripción", a heading that promises the operator's own
         * words and was instead delivering a machine's.
         *
         * <p>Everything that sentence said is already on the detail screen, composed from the
         * row: the heading reads "Ajuste #9" from transactions.type, the branch comes from the
         * movements, and the product is in the items table. Composing it at display time also
         * fixes the records already saved — see the same decision in InventoryMovementService.
         */
        String transactionDescription = request.reason() != null && !request.reason().isBlank()
                ? request.reason().trim()
                : null;

        TransactionEntity transaction = TransactionEntity.builder()
                .type(TransactionType.ADJUSTMENT)
                .status(TransactionStatus.CONFIRMED)
                .description(transactionDescription)
                .paymentMethod(null)
                .createdByUserEntity(createdBy)
                .total(BigDecimal.ZERO)
                .details(new ArrayList<>())
                .build();

        TransactionDetailEntity detail = TransactionDetailEntity.builder()
                .transaction(transaction)
                .product(product)
                // The line's own description is a fallback for rows with no product, and an
                // adjustment always has one, so this text never reached a screen. It was still a
                // frozen English string in a column the UI reads.
                .description(null)
                .quantity(adjustmentQuantity)
                .unitPrice(BigDecimal.ZERO)
                .subtotal(BigDecimal.ZERO)
                .build();

        transaction.getDetails().add(detail);

        TransactionEntity savedTransaction = transactionRepository.save(transaction);

        /*
          LOS DOS NÚMEROS VAN COMO DATOS, NO DENTRO DE UNA FRASE.

          Acá se armaba "Conteo: " + request.countedQuantity() + ", anterior: " +
          previousStock, y esa concatenación llamaba a BigDecimal.toString(), que no conoce
          el locale y siempre escribe punto decimal. En pantalla quedaba
          "Conteo: 9999.999, anterior: 12.530" — y ese segundo número es ambiguo justamente
          contra la regla del proyecto: en clase cantidad un punto solitario es decimal, así
          que son 12,530, pero quien audita stock lee doce mil quinientos treinta. Un factor
          mil, en la pantalla que existe para averiguar qué pasó con la mercadería.

          Peor todavía: el texto quedaba congelado en la base, así que arreglar esto no
          habría corregido nada de lo ya registrado. Ahora viajan los números y la frase la
          arma la pantalla con el mismo formateador que usa todo lo demás, y los movimientos
          nuevos salen bien sin migrar nada.

          `note` se queda SOLO con el motivo que escribió la persona, que sí es un dato y no
          se puede componer.
        */
        String movementNote = request.reason() != null && !request.reason().isBlank()
                ? request.reason().trim()
                : null;

        inventoryMovementService.registerAdjustmentMovement(
                savedTransaction,
                warehouse,
                product,
                movementType,
                adjustmentQuantity,
                movementNote,
                request.countedQuantity(),
                previousStock
        );

        return new CreateInventoryAdjustmentResponse(
                savedTransaction.getId(),
                product.getId(),
                product.getName(),
                warehouse.getId(),
                warehouse.getName(),
                movementType.name(),
                previousStock,
                request.countedQuantity(),
                adjustmentQuantity,
                "Inventory adjustment created successfully"
        );
    }
}