package com.harbeyescala.api_apuntalo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Contador correlativo de numeración comercial por Store (Fase 8.6). La
 * fila se bloquea con {@code PESSIMISTIC_WRITE} al pagar un ticket para que
 * la asignación de {@code nextNumber} y su incremento sean atómicos frente
 * a pagos concurrentes de la misma Store.
 */
@Entity
@Table(name = "ticket_number_sequences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketNumberSequence {

    @EmbeddedId
    private TicketNumberSequenceId id;

    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="store_id",insertable=false,updatable=false,nullable=false)
    private Store store;

    @Column(name = "next_number", nullable = false)
    private Long nextNumber;

    @Version
    @Column(nullable = false)
    private Long version;
}
