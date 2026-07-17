package com.harbeyescala.api_apuntalo.repository;

import com.harbeyescala.api_apuntalo.entity.TicketNumberSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TicketNumberSequenceRepository extends JpaRepository<TicketNumberSequence, Long> {

    /**
     * Bloquea la fila de secuencia del negocio para asignar el próximo
     * número comercial de forma atómica frente a pagos concurrentes
     * (Fase 5.1).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TicketNumberSequence s where s.negocioId = :negocioId")
    Optional<TicketNumberSequence> findByNegocioIdForUpdate(@Param("negocioId") Long negocioId);
}
