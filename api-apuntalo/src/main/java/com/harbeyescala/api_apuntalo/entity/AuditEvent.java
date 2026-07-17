package com.harbeyescala.api_apuntalo.entity;

import com.harbeyescala.api_apuntalo.entity.enums.AuditAction;
import com.harbeyescala.api_apuntalo.entity.enums.AuditEntityType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Evento de auditoría funcional (Fase 5.3). Guarda únicamente snapshots
 * saneados construidos explícitamente por el llamador (mapas simples), sin
 * serializar nunca entidades completas de Hibernate ni datos sensibles
 * (contraseñas, JWT, cabeceras de autorización).
 */
@Entity
@Table(
    name = "audit_events",
    indexes = {
        @Index(name = "idx_audit_events_negocio_occurred", columnList = "negocio_id, occurred_at"),
        @Index(name = "idx_audit_events_entity", columnList = "entity_type, entity_id"),
        @Index(name = "idx_audit_events_action", columnList = "action")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "negocio_id", nullable = false)
    private Long negocioId;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 80)
    private AuditEntityType entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    private AuditAction action;

    @Column(name = "previous_state_json", columnDefinition = "text")
    private String previousStateJson;

    @Column(name = "new_state_json", columnDefinition = "text")
    private String newStateJson;

    @Column(name = "occurred_at", nullable = false)
    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(nullable = false)
    private Boolean success;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;
}
