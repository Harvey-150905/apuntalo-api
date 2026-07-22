package com.harbeyescala.api_apuntalo.controller;

import com.harbeyescala.api_apuntalo.dto.AssignStoreRequestDto;
import com.harbeyescala.api_apuntalo.dto.BatchAssignStoresRequestDto;
import com.harbeyescala.api_apuntalo.dto.DefaultStoreRequestDto;
import com.harbeyescala.api_apuntalo.dto.UserStoreAccessResponseDto;
import com.harbeyescala.api_apuntalo.service.AdminAuthorizationService;
import com.harbeyescala.api_apuntalo.service.UserStoreAssignmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * Gestión administrativa de accesos usuario-tienda y Store predeterminada
 * (Fase 9, F9.5), bajo {@code /api/admin/users/{userId}/stores}.
 */
@RestController
@RequestMapping("/api/admin/users/{userId}/stores")
public class UserStoreAssignmentController {

    private final UserStoreAssignmentService assignmentService;
    private final AdminAuthorizationService adminAuth;

    public UserStoreAssignmentController(UserStoreAssignmentService assignmentService,
                                         AdminAuthorizationService adminAuth) {
        this.assignmentService = assignmentService;
        this.adminAuth = adminAuth;
    }

    @GetMapping
    public List<UserStoreAccessResponseDto> list(@PathVariable Long userId,
                                                 @RequestParam(required = false) Long negocioId) {
        Long tenantId = adminAuth.resolveAdminTenantId(Optional.ofNullable(negocioId));
        return assignmentService.list(tenantId, userId);
    }

    @PutMapping
    public List<UserStoreAccessResponseDto> batchAssign(
            @PathVariable Long userId,
            @Valid @RequestBody BatchAssignStoresRequestDto dto,
            @RequestParam(required = false) Long negocioId) {
        Long tenantId = adminAuth.resolveAdminTenantId(Optional.ofNullable(negocioId));
        return assignmentService.batchAssign(tenantId, userId, dto.getActiveStoreIds(), dto.getDefaultStoreId());
    }

    @PostMapping
    public List<UserStoreAccessResponseDto> assignOne(
            @PathVariable Long userId,
            @Valid @RequestBody AssignStoreRequestDto dto,
            @RequestParam(required = false) Long negocioId) {
        Long tenantId = adminAuth.resolveAdminTenantId(Optional.ofNullable(negocioId));
        return assignmentService.assignOne(tenantId, userId, dto.getStoreId(),
                Boolean.TRUE.equals(dto.getMakeDefault()));
    }

    @DeleteMapping("/{storeId}")
    public List<UserStoreAccessResponseDto> revoke(
            @PathVariable Long userId,
            @PathVariable Long storeId,
            @RequestParam(required = false) Long replacementDefaultStoreId,
            @RequestParam(required = false) Long negocioId) {
        Long tenantId = adminAuth.resolveAdminTenantId(Optional.ofNullable(negocioId));
        return assignmentService.revoke(tenantId, userId, storeId, replacementDefaultStoreId);
    }

    @PutMapping("/default-store")
    public List<UserStoreAccessResponseDto> setDefault(
            @PathVariable Long userId,
            @Valid @RequestBody DefaultStoreRequestDto dto,
            @RequestParam(required = false) Long negocioId) {
        Long tenantId = adminAuth.resolveAdminTenantId(Optional.ofNullable(negocioId));
        return assignmentService.setDefaultStore(tenantId, userId, dto.getDefaultStoreId());
    }
}
