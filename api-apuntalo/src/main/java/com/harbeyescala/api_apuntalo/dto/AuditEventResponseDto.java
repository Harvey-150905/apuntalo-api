package com.harbeyescala.api_apuntalo.dto;

import com.harbeyescala.api_apuntalo.entity.enums.AuditAction;
import com.harbeyescala.api_apuntalo.entity.enums.AuditEntityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEventResponseDto {

    private Long id;
    private Long negocioId;
    private Long storeId;
    private Boolean storeScoped;
    private Boolean legacyScope;
    private Long userId;
    private AuditEntityType entityType;
    private Long entityId;
    private AuditAction action;
    private String previousStateJson;
    private String newStateJson;
    private LocalDateTime occurredAt;
    private String idempotencyKey;
    private String requestId;
    private Boolean success;
    private String errorCode;
    private String metadataJson;
}
