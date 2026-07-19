package com.harbeyescala.api_apuntalo.controller;

import com.harbeyescala.api_apuntalo.dto.CashSessionResponseDto;
import com.harbeyescala.api_apuntalo.dto.CashSessionSummaryDto;
import com.harbeyescala.api_apuntalo.dto.PendingCashSessionTicketDto;
import com.harbeyescala.api_apuntalo.service.CashSessionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/cash-sessions")
public class CashSessionAdminController {
    private final CashSessionService service;

    public CashSessionAdminController(CashSessionService service) { this.service = service; }

    @GetMapping("/open")
    public List<CashSessionResponseDto> findOpen() { return service.findOpen(); }

    @GetMapping("/{id}")
    public CashSessionResponseDto findById(@PathVariable Long id) { return service.findById(id); }

    @GetMapping("/{id}/summary")
    public CashSessionSummaryDto findSummary(@PathVariable Long id) { return service.findSummary(id); }

    @GetMapping("/open/summaries")
    public List<CashSessionSummaryDto> findOpenSummaries() { return service.findOpenSummaries(); }

    @GetMapping("/{id}/pending-tickets")
    public List<PendingCashSessionTicketDto> findPendingTickets(@PathVariable Long id) {
        return service.findPendingTickets(id);
    }
}
