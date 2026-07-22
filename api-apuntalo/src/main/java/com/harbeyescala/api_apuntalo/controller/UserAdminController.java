package com.harbeyescala.api_apuntalo.controller;

import com.harbeyescala.api_apuntalo.dto.*;
import com.harbeyescala.api_apuntalo.entity.Role;
import com.harbeyescala.api_apuntalo.service.AdminAuthorizationService;
import com.harbeyescala.api_apuntalo.service.UserAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * CRUD administrativo de usuarios (Fase 9, F9.4), bajo {@code /api/admin/users}.
 * Un ADMIN administra usuarios dentro de su alcance; un SUPER_ADMIN puede
 * indicar {@code negocioId} para actuar sobre otro tenant.
 */
@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {

    private final UserAdminService userAdminService;
    private final AdminAuthorizationService adminAuth;

    public UserAdminController(UserAdminService userAdminService, AdminAuthorizationService adminAuth) {
        this.userAdminService = userAdminService;
        this.adminAuth = adminAuth;
    }

    @GetMapping
    public PageResponseDto<AdminUserResponseDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Long negocioId) {
        Long tenantId = adminAuth.resolveAdminTenantId(Optional.ofNullable(negocioId));
        return userAdminService.list(tenantId, q, active, role, page, size);
    }

    @GetMapping("/{id}")
    public AdminUserDetailResponseDto findById(@PathVariable Long id,
                                               @RequestParam(required = false) Long negocioId) {
        Long tenantId = adminAuth.resolveAdminTenantId(Optional.ofNullable(negocioId));
        return userAdminService.findById(tenantId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserDetailResponseDto create(@Valid @RequestBody AdminUserCreateRequestDto dto) {
        Long tenantId = adminAuth.resolveAdminTenantId(Optional.ofNullable(dto.getNegocioId()));
        return userAdminService.create(tenantId, dto);
    }

    @PutMapping("/{id}")
    public AdminUserDetailResponseDto update(@PathVariable Long id,
                                             @Valid @RequestBody AdminUserUpdateRequestDto dto,
                                             @RequestParam(required = false) Long negocioId) {
        Long tenantId = adminAuth.resolveAdminTenantId(Optional.ofNullable(negocioId));
        return userAdminService.update(tenantId, id, dto);
    }

    @PatchMapping("/{id}/status")
    public AdminUserDetailResponseDto setStatus(@PathVariable Long id,
                                                @Valid @RequestBody ActiveStatusRequestDto dto,
                                                @RequestParam(required = false) Long negocioId) {
        Long tenantId = adminAuth.resolveAdminTenantId(Optional.ofNullable(negocioId));
        return userAdminService.setActive(tenantId, id, dto.getActive());
    }

    @PostMapping("/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable Long id,
                              @Valid @RequestBody ResetPasswordRequestDto dto,
                              @RequestParam(required = false) Long negocioId) {
        Long tenantId = adminAuth.resolveAdminTenantId(Optional.ofNullable(negocioId));
        userAdminService.resetPassword(tenantId, id, dto.getNewPassword());
    }
}
