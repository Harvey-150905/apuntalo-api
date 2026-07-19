package com.harbeyescala.api_apuntalo.repository;

import com.harbeyescala.api_apuntalo.entity.Category;
import com.harbeyescala.api_apuntalo.entity.Subcategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubcategoryRepository extends JpaRepository<Subcategory, Long> {

    List<Subcategory> findByNegocioId(Long negocioId);
    List<Subcategory> findByNegocioIdAndStoreId(Long negocioId,Long storeId);
    Optional<Subcategory> findByIdAndNegocioIdAndStoreId(Long id,Long negocioId,Long storeId);
    boolean existsByNombreAndCategoryAndNegocioIdAndStoreId(String n,Category c,Long tenant,Long store);
    boolean existsByNombreAndCategoryAndNegocioIdAndStoreIdAndIdNot(String n,Category c,Long tenant,Long store,Long id);

    Optional<Subcategory> findByIdAndNegocioId(Long id, Long negocioId);

    boolean existsByNombreAndCategoryAndNegocioId(String nombre, Category category, Long negocioId);

    boolean existsByNombreAndCategoryAndNegocioIdAndIdNot(String nombre, Category category, Long negocioId, Long id);
}
