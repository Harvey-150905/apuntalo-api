package com.harbeyescala.api_apuntalo.controller;

import com.harbeyescala.api_apuntalo.dto.CashRegisterResponseDto;
import com.harbeyescala.api_apuntalo.service.CashRegisterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cash-registers")
public class CashRegisterController {
    private final CashRegisterService service;

    public CashRegisterController(CashRegisterService service) { this.service = service; }

    @GetMapping("/active")
    public List<CashRegisterResponseDto> findActive() { return service.findActive(); }
}
