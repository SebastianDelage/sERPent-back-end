package com.empresa.serpent.inventory.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.domain.enums.MovementType;
import com.empresa.serpent.inventory.repository.WarehouseRepository;
import com.empresa.serpent.inventory.web.dto.request.CreateSaleReturnRequest;
import com.empresa.serpent.inventory.web.dto.response.CreateSaleReturnResponse;
import com.empresa.serpent.inventory.web.dto.response.SaleReturnResponse;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.exception.ValidationException;
import com.empresa.serpent.transactions.domain.entity.SaleEntity;
import com.empresa.serpent.transactions.domain.entity.SaleReturnEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionDetailEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
import com.empresa.serpent.transactions.domain.enums.TransactionStatus;
import com.empresa.serpent.transactions.domain.enums.TransactionType;
import com.empresa.serpent.transactions.repository.SaleRepository;
import com.empresa.serpent.transactions.repository.SaleReturnRepository;
import com.empresa.serpent.transactions.repository.TransactionDetailRepository;
import com.empresa.serpent.transactions.repository.TransactionRepository;
import com.empresa.serpent.users.domain.entity.UserEntity;
import com.empresa.serpent.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SaleReturnApplicationService {

    private final TransactionRepository transactionRepository;
    private final TransactionDetailRepository transactionDetailRepository;
    private final SaleRepository saleRepository;
    private final SaleReturnRepository saleReturnRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final InventoryMovementService inventoryMovementService;

    @Transactional
    public CreateSaleReturnResponse createReturn(CreateSaleReturnRequest request) {

        UserEntity createdBy = userRepository.findById(request.createdByUserId())
                .orElseThrow(() -> new NotFoundException("User not found: " + request.createdByUserId()));

        ProductEntity product = productRepository.findById(request.productId())
                .orElseThrow(() -> new NotFoundException("Product not found: " + request.productId()));

        WarehouseEntity warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> new NotFoundException("Warehouse not found: " + request.warehouseId()));

        if (!Boolean.TRUE.equals(warehouse.getActive())) {
            throw new ValidationException("El depósito seleccionado está inactivo.");
        }

        SaleEntity originalSale = saleRepository.findById(request.saleId())
                .orElseThrow(() -> new NotFoundException("Sale not found: " + request.saleId()));

        TransactionEntity originalTransaction = originalSale.getTransaction();

        if (originalTransaction == null) {
            throw new ValidationException("La venta original no tiene una transacción asociada.");
        }

        if (originalTransaction.getType() != TransactionType.SALE) {
            throw new ValidationException("La transacción original no es una venta.");
        }

        BigDecimal soldQuantity = getSoldQuantity(originalTransaction.getId(), request.productId());

        if (soldQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "El producto \"" + product.getName() + "\" no forma parte de esta venta."
            );
        }

        BigDecimal alreadyReturnedQuantity = getAlreadyReturnedQuantity(request.saleId(), request.productId());

        BigDecimal remainingReturnableQuantity = soldQuantity.subtract(alreadyReturnedQuantity);

        if (remainingReturnableQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "Ya se devolvió toda la cantidad vendida de \"" + product.getName() + "\"."
            );
        }

        if (request.quantity().compareTo(remainingReturnableQuantity) > 0) {
            throw new ValidationException(
                    "No podés devolver esa cantidad de \"" + product.getName() + "\". "
                            + "Se vendieron " + soldQuantity
                            + " y ya se devolvieron " + alreadyReturnedQuantity + "."
            );
        }

        String description = request.reason() != null && !request.reason().isBlank()
                ? request.reason().trim()
                : "Devolución de la venta #" + request.saleId() + " — " + product.getName();

        TransactionEntity transaction = TransactionEntity.builder()
                .type(TransactionType.RETURN)
                .status(TransactionStatus.CONFIRMED)
                .description(description)
                .paymentMethod(null)
                .createdByUserEntity(createdBy)
                .total(BigDecimal.ZERO)
                .details(new ArrayList<>())
                .build();

        TransactionDetailEntity detail = TransactionDetailEntity.builder()
                .transaction(transaction)
                .product(product)
                .description("Devolución")
                .quantity(request.quantity())
                .unitPrice(BigDecimal.ZERO)
                .subtotal(BigDecimal.ZERO)
                .build();

        transaction.getDetails().add(detail);

        TransactionEntity savedTransaction = transactionRepository.save(transaction);

        SaleReturnEntity saleReturn = SaleReturnEntity.builder()
                .transaction(savedTransaction)
                .originalSale(originalSale)
                .reason(request.reason())
                .build();

        saleReturnRepository.save(saleReturn);

        inventoryMovementService.registerAdjustmentMovement(
                savedTransaction,
                warehouse,
                product,
                MovementType.RETURN_IN,
                request.quantity(),
                "Devolución de la venta #" + request.saleId()
        );

        return new CreateSaleReturnResponse(
                savedTransaction.getId(),
                originalSale.getId(),
                product.getId(),
                product.getName(),
                warehouse.getId(),
                warehouse.getName(),
                request.quantity(),
                "Devolución registrada correctamente."
        );
    }

    /** Returns every return registered against a sale, one row per returned product. */
    @Transactional(readOnly = true)
    public List<SaleReturnResponse> findBySaleId(Long saleId) {
        return saleReturnRepository.findByOriginalSaleId(saleId).stream()
                .flatMap(saleReturn -> saleReturn.getTransaction().getDetails().stream()
                        .map(detail -> new SaleReturnResponse(
                                saleReturn.getId(),
                                saleReturn.getTransaction().getId(),
                                saleReturn.getTransaction().getDate(),
                                saleId,
                                detail.getProduct().getId(),
                                detail.getProduct().getName(),
                                detail.getQuantity(),
                                saleReturn.getReason()
                        )))
                .toList();
    }

    private BigDecimal getSoldQuantity(Long saleTransactionId, Long productId) {
        return transactionDetailRepository.findByTransactionIdAndProductId(saleTransactionId, productId)
                .stream()
                .map(TransactionDetailEntity::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal getAlreadyReturnedQuantity(Long saleId, Long productId) {
        List<SaleReturnEntity> saleReturns = saleReturnRepository.findByOriginalSaleId(saleId);

        if (saleReturns.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return saleReturns.stream()
                .map(SaleReturnEntity::getTransaction)
                .filter(transaction -> transaction != null && transaction.getId() != null)
                .map(TransactionEntity::getId)
                .map(returnTransactionId ->
                        transactionDetailRepository.findByTransactionIdAndProductId(returnTransactionId, productId)
                )
                .flatMap(List::stream)
                .map(TransactionDetailEntity::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}