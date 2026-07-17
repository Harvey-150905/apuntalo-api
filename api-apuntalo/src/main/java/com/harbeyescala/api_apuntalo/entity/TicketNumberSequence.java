package com.harbeyescala.api_apuntalo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Contador correlativo de numeración comercial por negocio (Fase 5.1). La
 * fila se bloquea con {@code PESSIMISTIC_WRITE} al pagar un ticket para que
 * la asignación de {@code nextNumber} y su incremento sean atómicos frente
 * a pagos concurrentes del mismo negocio.
 */
@Entity
@Table(name = "ticket_number_sequences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketNumberSequence {

    @Id
    @Column(name = "negocio_id")
    private Long negocioId;

    @Column(name = "next_number", nullable = false)
    private Long nextNumber;

    @Version
    @Column(nullable = false)
    private Long version;
}
