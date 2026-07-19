package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.dto.CashManagementConfigRequestDto;
import com.harbeyescala.api_apuntalo.dto.CashManagementConfigResponseDto;
import com.harbeyescala.api_apuntalo.entity.Store;
import com.harbeyescala.api_apuntalo.entity.enums.AuditAction;
import com.harbeyescala.api_apuntalo.entity.enums.AuditEntityType;
import com.harbeyescala.api_apuntalo.repository.CashSessionRepository;
import com.harbeyescala.api_apuntalo.repository.StoreRepository;
import com.harbeyescala.api_apuntalo.entity.enums.CashSessionStatus;
import com.harbeyescala.api_apuntalo.exception.ConflictException;
import com.harbeyescala.api_apuntalo.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class CashManagementService {
    private final CurrentUser currentUser;
    private final AuditEventService auditEventService;
    private final CashSessionRepository cashSessionRepository;
    private final StoreRepository storeRepository;

    public CashManagementService(CurrentUser currentUser,
                                 AuditEventService auditEventService,
                                 CashSessionRepository cashSessionRepository,StoreRepository storeRepository) {
        this.currentUser = currentUser;
        this.auditEventService = auditEventService;
        this.cashSessionRepository = cashSessionRepository;
        this.storeRepository=storeRepository;
    }

    @Transactional(readOnly = true)
    public CashManagementConfigResponseDto getConfig() {
        return CashManagementConfigResponseDto.builder().enabled(currentStore(false).getCashReconciliationEnabled()).build();
    }

    @Transactional
    public CashManagementConfigResponseDto updateConfig(CashManagementConfigRequestDto request) {
        Store store = currentStore(true);
        boolean previous = Boolean.TRUE.equals(store.getCashReconciliationEnabled());
        boolean enabled = Boolean.TRUE.equals(request.getEnabled());
        if (previous != enabled && cashSessionRepository.existsByNegocioIdAndStoreIdAndStatus(
                currentUser.getTenantId(),store.getId(), CashSessionStatus.OPEN)) {
            throw new ConflictException("CASH_RECONCILIATION_HAS_OPEN_SESSIONS",
                    "No se puede cambiar la reconciliación mientras existan sesiones abiertas");
        }
        if (previous == enabled) {
            return CashManagementConfigResponseDto.builder().enabled(enabled).build();
        }
        store.setCashReconciliationEnabled(enabled);
        storeRepository.save(store);
        auditEventService.recordSuccess(
                AuditEntityType.NEGOCIO,
                store.getId(),
                enabled ? AuditAction.CASH_RECONCILIATION_ENABLED : AuditAction.CASH_RECONCILIATION_DISABLED,
                Map.of("cashReconciliationEnabled", previous),
                Map.of("cashReconciliationEnabled", enabled));
        return CashManagementConfigResponseDto.builder().enabled(enabled).build();
    }

    private Store currentStore(boolean forUpdate) {
        Long tenant=currentUser.getTenantId(), store=currentUser.requireCurrentStoreId();
        return (forUpdate?storeRepository.findByIdAndNegocioIdForUpdate(store,tenant)
                :storeRepository.findByIdAndNegocioId(store,tenant))
                .orElseThrow(com.harbeyescala.api_apuntalo.exception.StoreNotFoundException::new);
    }
}
