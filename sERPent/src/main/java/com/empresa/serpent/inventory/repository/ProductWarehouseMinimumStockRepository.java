package com.empresa.serpent.inventory.repository;

import com.empresa.serpent.inventory.domain.entity.ProductWarehouseMinimumStockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductWarehouseMinimumStockRepository
        extends JpaRepository<ProductWarehouseMinimumStockEntity, Long> {

    /** Every override for one product, to show the cascade resolved per warehouse. */
    List<ProductWarehouseMinimumStockEntity> findByProductId(Long productId);

    Optional<ProductWarehouseMinimumStockEntity> findByProductIdAndWarehouseId(
            Long productId, Long warehouseId);

    void deleteByProductIdAndWarehouseId(Long productId, Long warehouseId);
}
