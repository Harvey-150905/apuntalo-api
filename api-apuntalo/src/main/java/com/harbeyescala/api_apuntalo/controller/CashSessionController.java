package com.harbeyescala.api_apuntalo.controller;

import com.harbeyescala.api_apuntalo.dto.CashSessionResponseDto;
import com.harbeyescala.api_apuntalo.dto.IdempotentOutcome;
import com.harbeyescala.api_apuntalo.dto.OpenCashSessionRequestDto;
import com.harbeyescala.api_apuntalo.service.CashSessionService;
import com.harbeyescala.api_apuntalo.service.IdempotencyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cash-sessions")
public class CashSessionController {
    private final CashSessionService service;
    private final IdempotencyService idempotencyService;

    public CashSessionController(CashSessionService service, IdempotencyService idempotencyService) {
        this.service = service;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping("/open")
    public ResponseEntity<CashSessionResponseDto> open(
            @Valid @RequestBody OpenCashSessionRequestDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        IdempotentOutcome<CashSessionResponseDto> outcome = idempotencyService.execute(
                "CASH_SESSION_OPEN", "CASH_SESSION", key, request,
                HttpStatus.CREATED.value(), CashSessionResponseDto.class,
                () -> service.open(request));
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(outcome.status());
        if (outcome.replayed()) builder.header("Idempotency-Replayed", "true");
        return builder.body(outcome.body());
    }

    @GetMapping("/my-open")
    public ResponseEntity<CashSessionResponseDto> findMyOpen() {
        return ResponseEntity.ok(service.findMyOpen());
    }

    @GetMapping("/open")
    public List<CashSessionResponseDto> findOpen() { return service.findOpen(); }
}
