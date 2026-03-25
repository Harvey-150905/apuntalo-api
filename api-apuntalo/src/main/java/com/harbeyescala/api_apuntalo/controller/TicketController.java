package com.harbeyescala.api_apuntalo.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.harbeyescala.api_apuntalo.dto.AddTicketLinesRequestDto;
import com.harbeyescala.api_apuntalo.dto.CashClosingSummaryDto;
import com.harbeyescala.api_apuntalo.dto.ChangeTicketMesaRequestDto;
import com.harbeyescala.api_apuntalo.dto.PageResponseDto;
import com.harbeyescala.api_apuntalo.dto.PayTicketRequestDto;
import com.harbeyescala.api_apuntalo.dto.PaymentMethodSummaryDto;
import com.harbeyescala.api_apuntalo.dto.TicketDetailResponseDto;
import com.harbeyescala.api_apuntalo.dto.TicketRequestDto;
import com.harbeyescala.api_apuntalo.dto.TicketResponseDto;
import com.harbeyescala.api_apuntalo.dto.UpdateTicketNotesRequestDto;
import com.harbeyescala.api_apuntalo.dto.UserSalesSummaryDto;
import com.harbeyescala.api_apuntalo.dto.ProductSalesSummaryDto;
import com.harbeyescala.api_apuntalo.dto.DailySalesSummaryDto;
import com.harbeyescala.api_apuntalo.dto.AverageTicketSummaryDto;
import com.harbeyescala.api_apuntalo.service.TicketService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponseDto create(@Valid @RequestBody TicketRequestDto dto) {
        return ticketService.create(dto);
    }

    @GetMapping("/mesa/{mesaId}")
    public TicketResponseDto findOpenByMesa(@PathVariable Long mesaId) {
        return ticketService.findOpenByMesa(mesaId);
    }

    @GetMapping("/{ticketId}")
    public TicketDetailResponseDto findDetailById(@PathVariable Long ticketId) {
        return ticketService.findDetailById(ticketId);
    }

    @PostMapping("/{ticketId}/lines")
    public TicketResponseDto addLines(
            @PathVariable Long ticketId,
            @Valid @RequestBody AddTicketLinesRequestDto dto
    ) {
        return ticketService.addLines(ticketId, dto);
    }

    @PostMapping("/{ticketId}/pay")
    public TicketDetailResponseDto pay(
            @PathVariable Long ticketId,
            @Valid @RequestBody PayTicketRequestDto dto
    ) {
        return ticketService.pay(ticketId, dto);
    }

    @PatchMapping("/{ticketId}/lines/{lineId}/cancel")
    public TicketDetailResponseDto cancelLine(
            @PathVariable Long ticketId,
            @PathVariable Long lineId
    ) {
        return ticketService.cancelLine(ticketId, lineId);
    }
    @PatchMapping("/{ticketId}/cancel")
    public TicketDetailResponseDto cancelTicket(@PathVariable Long ticketId) {
        return ticketService.cancelTicket(ticketId);
    }

    @PatchMapping("/{ticketId}/batches/{batchNumber}/cancel")
    public TicketDetailResponseDto cancelBatch(
            @PathVariable Long ticketId,
            @PathVariable Integer batchNumber
    ) {
        return ticketService.cancelBatch(ticketId, batchNumber);
    }

    @GetMapping("/paid")
    public ResponseEntity<PageResponseDto<TicketResponseDto>> getPaidTickets(
        @RequestParam LocalDate from,
        @RequestParam LocalDate to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(
            ticketService.findPaidTicketsPaged(from, to, page, size)
        );
    }
    
    @GetMapping("/open")
    public ResponseEntity<PageResponseDto<TicketResponseDto>> getOpenTickets(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ticketService.findOpenTicketsPaged(page, size));
    }
    @GetMapping("/cancelled")
    public ResponseEntity<PageResponseDto<TicketResponseDto>> getCancelledTickets(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ticketService.findCancelledTicketsPaged(page, size));
    }
    @GetMapping("/paid/total")
    public BigDecimal getTotalSales(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to
    ) {
        return ticketService.getTotalSales(from, to);
    }

    @GetMapping("/paid/payment-summary")
    public PaymentMethodSummaryDto getPaymentMethodSummary(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to
    ) {
        return ticketService.getPaymentMethodSummary(from, to);
    }
    @GetMapping("/paid/user-summary")
    public List<UserSalesSummaryDto> getUserSalesSummary(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to
    ) {
        return ticketService.getUserSalesSummary(from, to);
    }
    @GetMapping("/cash-closing")
    public CashClosingSummaryDto getCashClosingSummary(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to
    ) {
        return ticketService.getCashClosingSummary(from, to);
    }
    @PatchMapping("/{ticketId}/notes")
    public TicketDetailResponseDto updateNotes(
            @PathVariable Long ticketId,
            @Valid @RequestBody UpdateTicketNotesRequestDto dto
    ) {
        return ticketService.updateNotes(ticketId, dto);
    }
    @PatchMapping("/{ticketId}/mesa")
    public TicketDetailResponseDto changeMesa(
            @PathVariable Long ticketId,
            @Valid @RequestBody ChangeTicketMesaRequestDto dto
    ) {
        return ticketService.changeMesa(ticketId, dto);
    }
    @GetMapping("/paid/product-summary")
    public List<ProductSalesSummaryDto> getProductSalesSummary(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to
    ) {
        return ticketService.getProductSalesSummary(from, to);
    }

    @GetMapping("/paid/daily-summary")
    public List<DailySalesSummaryDto> getDailySalesSummary(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to
    ) {
        return ticketService.getDailySalesSummary(from, to);
    }

    @GetMapping("/paid/average-ticket")
    public AverageTicketSummaryDto getAverageTicketSummary(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to
    ) {
        return ticketService.getAverageTicketSummary(from, to);
    }
}