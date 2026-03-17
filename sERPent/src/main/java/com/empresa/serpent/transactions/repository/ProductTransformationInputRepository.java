package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.transactions.domain.entity.ProductTransformationInputEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductTransformationInputRepository extends JpaRepository<ProductTransformationInputEntity, Long> {
}