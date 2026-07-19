package com.harbeyescala.api_apuntalo.controller;

import com.harbeyescala.api_apuntalo.dto.*;
import com.harbeyescala.api_apuntalo.service.CashRegisterService;
import com.harbeyescala.api_apuntalo.service.IdempotencyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Locale;

@RestController
@RequestMapping("/api/admin/cash-registers")
public class CashRegisterAdminController {
    private final CashRegisterService service;
    private final IdempotencyService idempotencyService;

    public CashRegisterAdminController(CashRegisterService service, IdempotencyService idempotencyService) {
        this.service = service;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping
    public ResponseEntity<CashRegisterResponseDto> create(
            @Valid @RequestBody CashRegisterNameRequestDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return response(idempotencyService.execute("CASH_REGISTER_CREATE", "CASH_REGISTER", key,
                Map.of("name", canonicalName(request.getName())), HttpStatus.CREATED.value(),
                CashRegisterResponseDto.class, () -> service.create(request)));
    }

    @GetMapping
    public List<CashRegisterResponseDto> findAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public CashRegisterResponseDto findById(@PathVariable Long id) { return service.findById(id); }

    @PatchMapping("/{id}")
    public ResponseEntity<CashRegisterResponseDto> rename(
            @PathVariable Long id, @Valid @RequestBody CashRegisterNameRequestDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return response(idempotencyService.execute("CASH_REGISTER_UPDATE", "CASH_REGISTER", key,
                Map.of("id", id, "name", canonicalName(request.getName())), 200, CashRegisterResponseDto.class,
                () -> service.rename(id, request)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<CashRegisterResponseDto> updateStatus(
            @PathVariable Long id, @Valid @RequestBody CashRegisterStatusRequestDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return response(idempotencyService.execute("CASH_REGISTER_STATUS_UPDATE", "CASH_REGISTER", key,
                Map.of("id", id, "body", request), 200, CashRegisterResponseDto.class,
                () -> service.updateStatus(id, request)));
    }

    private <T> ResponseEntity<T> response(IdempotentOutcome<T> outcome) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(outcome.status());
        if (outcome.replayed()) builder.header("Idempotency-Replayed", "true");
        return builder.body(outcome.body());
    }

    private String canonicalName(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
