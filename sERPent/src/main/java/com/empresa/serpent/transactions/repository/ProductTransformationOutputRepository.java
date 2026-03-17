package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.transactions.domain.entity.ProductTransformationOutputEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductTransformationOutputRepository extends JpaRepository<ProductTransformationOutputEntity, Long> {
}