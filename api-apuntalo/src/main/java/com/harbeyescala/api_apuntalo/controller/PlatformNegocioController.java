package com.harbeyescala.api_apuntalo.controller;

import com.harbeyescala.api_apuntalo.dto.ActiveStatusRequestDto;
import com.harbeyescala.api_apuntalo.dto.NegocioResponseDto;
import com.harbeyescala.api_apuntalo.dto.TenantProvisionRequestDto;
import com.harbeyescala.api_apuntalo.dto.TenantProvisionResponseDto;
import com.harbeyescala.api_apuntalo.service.NegocioService;
import com.harbeyescala.api_apuntalo.service.TenantProvisioningService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints de plataforma (Fase 9). Solo accesibles por SUPER_ADMIN (ver
 * {@code SecurityConfig}). Cubren el provisionamiento atómico de negocios y
 * el ciclo de vida (activación/desactivación) del negocio.
 */
@RestController
@RequestMapping("/api/platform/negocios")
public class PlatformNegocioController {

    private final TenantProvisioningService provisioningService;
    private final NegocioService negocioService;

    public PlatformNegocioController(TenantProvisioningService provisioningService,
                                     NegocioService negocioService) {
        this.provisioningService = provisioningService;
        this.negocioService = negocioService;
    }

    @PostMapping("/provision")
    @ResponseStatus(HttpStatus.CREATED)
    public TenantProvisionResponseDto provision(@Valid @RequestBody TenantProvisionRequestDto dto) {
        return provisioningService.provision(dto);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<NegocioResponseDto> setStatus(
            @PathVariable Long id, @Valid @RequestBody ActiveStatusRequestDto dto) {
        return ResponseEntity.ok(negocioService.setActive(id, dto.getActive()));
    }
}
