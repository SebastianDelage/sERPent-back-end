package com.empresa.serpent.transactions.repository;


import com.empresa.serpent.transactions.domain.entity.ExpenseCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategoryEntity, Long> {

    Optional<ExpenseCategoryEntity> findByNameIgnoreCase(String name);

    List<ExpenseCategoryEntity> findByActiveTrue();

    List<ExpenseCategoryEntity> findByActiveTrueAndNameContainingIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}