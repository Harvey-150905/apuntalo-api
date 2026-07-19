package com.harbeyescala.api_apuntalo.repository;

import com.harbeyescala.api_apuntalo.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByNegocioId(Long negocioId);
    List<Product> findByNegocioIdAndStoreId(Long negocioId,Long storeId);
    List<Product> findByNegocioIdAndStoreIdAndActivoTrue(Long negocioId,Long storeId);
    Optional<Product> findByIdAndNegocioIdAndStoreId(Long id,Long negocioId,Long storeId);
    boolean existsByNameAndNegocioIdAndStoreId(String name,Long negocioId,Long storeId);
    boolean existsByNameAndNegocioIdAndStoreIdAndIdNot(String name,Long negocioId,Long storeId,Long id);

    List<Product> findByNegocioIdAndActivoTrue(Long negocioId);

    Optional<Product> findByIdAndNegocioId(Long id, Long negocioId);

    boolean existsByNameAndNegocioId(String name, Long negocioId);

    boolean existsByNameAndNegocioIdAndIdNot(String name, Long negocioId, Long id);
}
