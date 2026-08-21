package com.empresa.serpent.cashcount.repository;

import com.empresa.serpent.cashcount.domain.entity.CashCountEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CashCountRepository extends JpaRepository<CashCountEntity, Long> {

    /**
     * The anchor: where the shift in progress started.
     *
     * <p>Ordered by {@code closedAt} and then by id, so two counts saved in the same instant
     * still have one definite last. Empty means this branch has never been counted, and the
     * period runs from the first record there is.
     */
    Optional<CashCountEntity> findFirstByWarehouseIdOrderByClosedAtDescIdDesc(Long warehouseId);

    /**
     * The history, newest first, restricted to what the caller may see.
     *
     * <p>{@code unrestricted} carries the ADMIN case rather than an empty list, because an
     * empty {@code IN} is a portability trap — callers short-circuit before getting here
     * when the scope is empty.
     */
    @Query("""
           SELECT c FROM CashCountEntity c
           WHERE (:unrestricted = TRUE OR c.warehouse.id IN :warehouseIds)
             AND (:warehouseId IS NULL OR c.warehouse.id = :warehouseId)
           ORDER BY c.closedAt DESC, c.id DESC
           """)
    Page<CashCountEntity> search(
            @Param("unrestricted") boolean unrestricted,
            @Param("warehouseIds") List<Long> warehouseIds,
            @Param("warehouseId") Long warehouseId,
            Pageable pageable
    );
}
