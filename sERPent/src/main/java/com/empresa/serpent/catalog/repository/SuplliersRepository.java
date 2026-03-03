package com.empresa.serpent.catalog.repository;

import com.empresa.serpent.catalog.domain.Suppliers;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SuplliersRepository extends JpaRepository<Suppliers,Long> {
    List<Suppliers> findByName(String name);
    List<Suppliers> findByDate(String name);


}
