package com.harbeyescala.api_apuntalo.repository;

import com.harbeyescala.api_apuntalo.entity.TicketNumberSequence;
import com.harbeyescala.api_apuntalo.entity.TicketNumberSequenceId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TicketNumberSequenceRepository extends JpaRepository<TicketNumberSequence, TicketNumberSequenceId> {

    /**
     * Bloquea la fila de secuencia de la Store para asignar el próximo
     * número comercial de forma atómica frente a pagos concurrentes
     * (Fase 8.6).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TicketNumberSequence s where s.id.negocioId=:negocioId and s.id.storeId=:storeId")
    Optional<TicketNumberSequence> findByNegocioIdAndStoreIdForUpdate(
      @Param("negocioId")Long negocioId,@Param("storeId")Long storeId);

    @Modifying
    @Query(value="insert into ticket_number_sequences(negocio_id,store_id,next_number,version) values(:negocioId,:storeId,1,0) on conflict(negocio_id,store_id) do nothing",nativeQuery=true)
    int initializeIfAbsent(@Param("negocioId")Long negocioId,@Param("storeId")Long storeId);
}
