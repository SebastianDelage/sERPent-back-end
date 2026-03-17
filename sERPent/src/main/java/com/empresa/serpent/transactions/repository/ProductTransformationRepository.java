package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.transactions.domain.entity.ProductTransformationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductTransformationRepository extends JpaRepository<ProductTransformationEntity, Long> {
}