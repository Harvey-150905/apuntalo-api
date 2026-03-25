package com.harbeyescala.api_apuntalo.repository;

import com.harbeyescala.api_apuntalo.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByNegocioId(Long negocioId);

    List<Product> findByNegocioIdAndActivoTrue(Long negocioId);

    Optional<Product> findByIdAndNegocioId(Long id, Long negocioId);

    boolean existsByNameAndNegocioId(String name, Long negocioId);

    boolean existsByNameAndNegocioIdAndIdNot(String name, Long negocioId, Long id);
}