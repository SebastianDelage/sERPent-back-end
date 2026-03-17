package com.empresa.serpent.transactions.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.repository.WarehouseRepository;
import com.empresa.serpent.inventory.service.InventoryMovementService;
import com.empresa.serpent.inventory.service.StockValidationService;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.transactions.domain.entity.ProductTransformationEntity;
import com.empresa.serpent.transactions.domain.entity.ProductTransformationInputEntity;
import com.empresa.serpent.transactions.domain.entity.ProductTransformationOutputEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
import com.empresa.serpent.transactions.domain.enums.TransactionStatus;
import com.empresa.serpent.transactions.domain.enums.TransactionType;
import com.empresa.serpent.transactions.repository.ProductTransformationRepository;
import com.empresa.serpent.transactions.repository.TransactionRepository;
import com.empresa.serpent.transactions.web.dto.request.CreateProductTransformationInputRequest;
import com.empresa.serpent.transactions.web.dto.request.CreateProductTransformationOutputRequest;
import com.empresa.serpent.transactions.web.dto.request.CreateProductTransformationRequest;
import com.empresa.serpent.transactions.web.dto.response.CreateProductTransformationResponse;
import com.empresa.serpent.users.domain.entity.UserEntity;
import com.empresa.serpent.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductTransformationApplicationService {

    private final TransactionRepository transactionRepository;
    private final ProductTransformationRepository productTransformationRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockValidationService stockValidationService;
    private final InventoryMovementService inventoryMovementService;

    @Transactional
    public CreateProductTransformationResponse createTransformation(CreateProductTransformationRequest request) {

        UserEntity createdBy = userRepository.findById(request.createdByUserId())
                .orElseThrow(() ->
                        new NotFoundException("User not found: " + request.createdByUserId()));

        WarehouseEntity warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() ->
                        new NotFoundException("Warehouse not found: " + request.warehouseId()));

        if (!Boolean.TRUE.equals(warehouse.getActive())) {
            throw new IllegalArgumentException("Warehouse is inactive: " + request.warehouseId());
        }

        if (request.inputs() == null || request.inputs().isEmpty()) {
            throw new IllegalArgumentException("Transformation must contain at least one input");
        }

        if (request.outputs() == null || request.outputs().isEmpty()) {
            throw new IllegalArgumentException("Transformation must contain at least one output");
        }

        validateInputs(request.inputs());
        validateOutputs(request.outputs());

        Set<Long> productIds = new HashSet<>();
        request.inputs().forEach(input -> productIds.add(input.productId()));
        request.outputs().forEach(output -> productIds.add(output.productId()));

        List<ProductEntity> products = productRepository.findByIdIn(productIds);

        Map<Long, ProductEntity> productMap = products.stream()
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));

        validateProductsExist(request.inputs(), request.outputs(), productMap);

        for (CreateProductTransformationInputRequest input : request.inputs()) {
            stockValidationService.validateAvailableStock(
                    input.productId(),
                    warehouse.getId(),
                    input.quantity()
            );
        }

        TransactionEntity transaction = TransactionEntity.builder()
                .type(TransactionType.TRANSFORMATION)
                .status(TransactionStatus.CONFIRMED)
                .description(normalizeOptional(request.description()))
                .createdByUserEntity(createdBy)
                .total(BigDecimal.ZERO)
                .build();

        TransactionEntity savedTransaction = transactionRepository.save(transaction);

        ProductTransformationEntity transformation = ProductTransformationEntity.builder()
                .transaction(savedTransaction)
                .warehouse(warehouse)
                .notes(normalizeOptional(request.notes()))
                .inputs(new ArrayList<>())
                .outputs(new ArrayList<>())
                .build();

        for (CreateProductTransformationInputRequest inputRequest : request.inputs()) {
            ProductEntity product = productMap.get(inputRequest.productId());

            ProductTransformationInputEntity input = ProductTransformationInputEntity.builder()
                    .transformation(transformation)
                    .product(product)
                    .description(resolveLineDescription(inputRequest.description(), product.getName()))
                    .quantity(inputRequest.quantity())
                    .build();

            transformation.getInputs().add(input);
        }

        for (CreateProductTransformationOutputRequest outputRequest : request.outputs()) {
            ProductEntity product = productMap.get(outputRequest.productId());

            ProductTransformationOutputEntity output = ProductTransformationOutputEntity.builder()
                    .transformation(transformation)
                    .product(product)
                    .description(resolveLineDescription(outputRequest.description(), product.getName()))
                    .quantity(outputRequest.quantity())
                    .build();

            transformation.getOutputs().add(output);
        }

        ProductTransformationEntity savedTransformation = productTransformationRepository.save(transformation);

        inventoryMovementService.registerTransformationMovements(savedTransformation);

        return new CreateProductTransformationResponse(
                savedTransaction.getId(),
                savedTransformation.getId(),
                savedTransaction.getStatus().name(),
                "Transformation created successfully"
        );
    }

    private void validateInputs(List<CreateProductTransformationInputRequest> inputs) {
        for (CreateProductTransformationInputRequest input : inputs) {
            if (input.productId() == null) {
                throw new IllegalArgumentException("Input productId cannot be null");
            }

            if (input.quantity() == null) {
                throw new IllegalArgumentException("Input quantity cannot be null");
            }

            if (input.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Input quantity must be greater than zero");
            }
        }
    }

    private void validateOutputs(List<CreateProductTransformationOutputRequest> outputs) {
        for (CreateProductTransformationOutputRequest output : outputs) {
            if (output.productId() == null) {
                throw new IllegalArgumentException("Output productId cannot be null");
            }

            if (output.quantity() == null) {
                throw new IllegalArgumentException("Output quantity cannot be null");
            }

            if (output.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Output quantity must be greater than zero");
            }
        }
    }

    private void validateProductsExist(
            List<CreateProductTransformationInputRequest> inputs,
            List<CreateProductTransformationOutputRequest> outputs,
            Map<Long, ProductEntity> productMap
    ) {
        for (CreateProductTransformationInputRequest input : inputs) {
            if (!productMap.containsKey(input.productId())) {
                throw new NotFoundException("Product not found: " + input.productId());
            }
        }

        for (CreateProductTransformationOutputRequest output : outputs) {
            if (!productMap.containsKey(output.productId())) {
                throw new NotFoundException("Product not found: " + output.productId());
            }
        }
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private String resolveLineDescription(String value, String fallback) {
        String normalized = normalizeOptional(value);
        return normalized != null ? normalized : fallback;
    }
}