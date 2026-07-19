package com.harbeyescala.api_apuntalo.controller;

import com.harbeyescala.api_apuntalo.dto.CashManagementConfigRequestDto;
import com.harbeyescala.api_apuntalo.dto.CashManagementConfigResponseDto;
import com.harbeyescala.api_apuntalo.dto.IdempotentOutcome;
import com.harbeyescala.api_apuntalo.service.CashManagementService;
import com.harbeyescala.api_apuntalo.service.IdempotencyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/admin/cash-management/config", "/api/admin/cash-reconciliation/config"})
public class CashManagementController {
    // El path cash-management se conserva por compatibilidad; "enabled"
    // representa desde V6.4 la reconciliación de efectivo, no el uso de sesiones.
    private final CashManagementService service;
    private final IdempotencyService idempotencyService;

    public CashManagementController(CashManagementService service, IdempotencyService idempotencyService) {
        this.service = service;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping
    public CashManagementConfigResponseDto getConfig() {
        return service.getConfig();
    }

    @PatchMapping
    public ResponseEntity<CashManagementConfigResponseDto> updateConfig(
            @Valid @RequestBody CashManagementConfigRequestDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        IdempotentOutcome<CashManagementConfigResponseDto> outcome = idempotencyService.execute(
                "CASH_MANAGEMENT_CONFIG_UPDATE", "NEGOCIO", idempotencyKey, request, 200,
                CashManagementConfigResponseDto.class, () -> service.updateConfig(request));
        return response(outcome);
    }

    private <T> ResponseEntity<T> response(IdempotentOutcome<T> outcome) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(outcome.status());
        if (outcome.replayed()) builder.header("Idempotency-Replayed", "true");
        return builder.body(outcome.body());
    }
}
