package com.harbeyescala.api_apuntalo.repository;

import com.harbeyescala.api_apuntalo.entity.Mesa;
import com.harbeyescala.api_apuntalo.entity.enums.MesaStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MesaRepository extends JpaRepository<Mesa, Long> {

    List<Mesa> findByNegocioId(Long negocioId);

    List<Mesa> findByNegocioIdAndActivaTrue(Long negocioId);

    List<Mesa> findByNegocioIdAndStatusAndActivaTrue(Long negocioId, MesaStatus status);

    Optional<Mesa> findByIdAndNegocioId(Long id, Long negocioId);

    boolean existsByNumeroAndNegocioId(Integer numero, Long negocioId);

    boolean existsByNumeroAndNegocioIdAndIdNot(Integer numero, Long negocioId, Long id);

    /**
     * Carga la mesa bajo bloqueo pesimista de escritura. Se usa para crear
     * ticket, cambiar de mesa o cualquier operación que asigne/libere una
     * mesa, de forma que comprobar-y-ocupar sea atómico (Fase 3.4).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Mesa m where m.id = :mesaId and m.negocio.id = :tenantId")
    Optional<Mesa> findByIdAndNegocioIdForUpdate(
            @Param("mesaId") Long mesaId,
            @Param("tenantId") Long tenantId
    );
}
