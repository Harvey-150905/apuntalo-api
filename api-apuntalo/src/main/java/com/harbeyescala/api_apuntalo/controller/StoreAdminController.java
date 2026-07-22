package com.harbeyescala.api_apuntalo.controller;

import com.harbeyescala.api_apuntalo.dto.ActiveStatusRequestDto;
import com.harbeyescala.api_apuntalo.dto.PageResponseDto;
import com.harbeyescala.api_apuntalo.dto.StoreCreateRequestDto;
import com.harbeyescala.api_apuntalo.dto.StoreResponseDto;
import com.harbeyescala.api_apuntalo.dto.StoreUpdateRequestDto;
import com.harbeyescala.api_apuntalo.service.AdminAuthorizationService;
import com.harbeyescala.api_apuntalo.service.StoreAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * CRUD administrativo de Stores (Fase 9, F9.3), bajo {@code /api/admin/stores}.
 * Un ADMIN opera sobre sus Stores autorizadas dentro de su tenant; un
 * SUPER_ADMIN puede indicar {@code negocioId} para actuar sobre otro tenant.
 */
@RestController
@RequestMapping("/api/admin/stores")
public class StoreAdminController {

    private final StoreAdminService storeAdminService;
    private final AdminAuthorizationService adminAuth;

    public StoreAdminController(StoreAdminService storeAdminService,
                                AdminAuthorizationService adminAuth) {
        this.storeAdminService = storeAdminService;
        this.adminAuth = adminAuth;
    }

    @GetMapping
    public PageResponseDto<StoreResponseDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Long negocioId) {
        Long tenantId = adminAuth.resolveAdminTenantId(Optional.ofNullable(negocioId));
        return storeAdminService.list(tenantId, q, active, page, size);
    }

    @GetMapping("/{id}")
    public StoreResponseDto findById(@PathVariable Long id,
                                     @RequestParam(required = false) Long negocioId) {
        Long tenantId = adminAuth.resolveAdminTenantId(Optional.ofNullable(negocioId));
        return storeAdminService.findById(tenantId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StoreResponseDto create(@Valid @RequestBody StoreCreateRequestDto dto) {
        Long tenantId = adminAuth.resolveAdminTenantId(Optional.ofNullable(dto.getNegocioId()));
        return storeAdminService.create(tenantId, dto);
    }

    @PutMapping("/{id}")
    public StoreResponseDto update(@PathVariable Long id,
                                   @Valid @RequestBody StoreUpdateRequestDto dto,
                                   @RequestParam(required = false) Long negocioId) {
        Long tenantId = adminAuth.resolveAdminTenantId(Optional.ofNullable(negocioId));
        return storeAdminService.update(tenantId, id, dto);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<StoreResponseDto> setStatus(@PathVariable Long id,
                                                      @Valid @RequestBody ActiveStatusRequestDto dto,
                                                      @RequestParam(required = false) Long negocioId) {
        Long tenantId = adminAuth.resolveAdminTenantId(Optional.ofNullable(negocioId));
        return ResponseEntity.ok(storeAdminService.setActive(tenantId, id, dto.getActive()));
    }
}
