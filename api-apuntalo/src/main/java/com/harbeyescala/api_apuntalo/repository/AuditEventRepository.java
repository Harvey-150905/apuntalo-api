package com.harbeyescala.api_apuntalo.repository;

import com.harbeyescala.api_apuntalo.entity.AuditEvent;
import com.harbeyescala.api_apuntalo.entity.enums.AuditAction;
import com.harbeyescala.api_apuntalo.entity.enums.AuditEntityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    /**
     * Listado filtrado y paginado para {@code GET /api/admin/audit-events}
     * (Fase 5.3). Todos los filtros son opcionales salvo el tenant, que
     * siempre se aplica para no filtrar entre negocios.
     */
    @Query("""
        SELECT e FROM AuditEvent e
        WHERE e.negocioId = :negocioId
        AND (:entityType IS NULL OR e.entityType = :entityType)
        AND (:entityId IS NULL OR e.entityId = :entityId)
        AND (:action IS NULL OR e.action = :action)
        AND (:success IS NULL OR e.success = :success)
        AND (:from IS NULL OR e.occurredAt >= :from)
        AND (:to IS NULL OR e.occurredAt <= :to)
        ORDER BY e.occurredAt DESC
    """)
    Page<AuditEvent> findFiltered(
        @Param("negocioId") Long negocioId,
        @Param("entityType") AuditEntityType entityType,
        @Param("entityId") Long entityId,
        @Param("action") AuditAction action,
        @Param("success") Boolean success,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        Pageable pageable
    );
}
