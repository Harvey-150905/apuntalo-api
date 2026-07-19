package com.harbeyescala.api_apuntalo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import com.harbeyescala.api_apuntalo.entity.Negocio;

public interface NegocioRepository extends JpaRepository<Negocio, Long> {
    List<Negocio> findByActivoTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Negocio n where n.id = :id")
    Optional<Negocio> findByIdForUpdate(@Param("id") Long id);
}
