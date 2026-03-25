package com.harbeyescala.api_apuntalo.repository;

import com.harbeyescala.api_apuntalo.entity.Mesa;
import com.harbeyescala.api_apuntalo.entity.enums.MesaStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MesaRepository extends JpaRepository<Mesa, Long> {

    List<Mesa> findByNegocioId(Long negocioId);

    List<Mesa> findByNegocioIdAndActivaTrue(Long negocioId);

    List<Mesa> findByNegocioIdAndStatusAndActivaTrue(Long negocioId, MesaStatus status);

    Optional<Mesa> findByIdAndNegocioId(Long id, Long negocioId);

    boolean existsByNumeroAndNegocioId(Integer numero, Long negocioId);

    boolean existsByNumeroAndNegocioIdAndIdNot(Integer numero, Long negocioId, Long id);
}