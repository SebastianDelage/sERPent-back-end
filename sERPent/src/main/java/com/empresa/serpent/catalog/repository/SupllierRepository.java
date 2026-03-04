package com.empresa.serpent.catalog.repository;

import com.empresa.serpent.catalog.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SupllierRepository extends JpaRepository<Supplier,Long> {
    List<Supplier> findByName(String name);
}
