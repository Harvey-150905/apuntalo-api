package com.harbeyescala.api_apuntalo.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.harbeyescala.api_apuntalo.dto.AddTicketLinesRequestDto;
import com.harbeyescala.api_apuntalo.dto.ApplyLineDiscountRequestDto;
import com.harbeyescala.api_apuntalo.dto.CashClosingSummaryDto;
import com.harbeyescala.api_apuntalo.dto.ChangeTicketMesaRequestDto;
import com.harbeyescala.api_apuntalo.dto.IdempotentOutcome;
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
import com.harbeyescala.api_apuntalo.service.IdempotencyService;
import com.harbeyescala.api_apuntalo.service.TicketService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final TicketService ticketService;
    private final IdempotencyService idempotencyService;

    public TicketController(TicketService ticketService, IdempotencyService idempotencyService) {
        this.ticketService = ticketService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping
    public ResponseEntity<TicketResponseDto> create(
            @Valid @RequestBody TicketRequestDto dto,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey
    ) {
        IdempotentOutcome<TicketResponseDto> outcome = idempotencyService.execute(
                "TICKET_CREATE",
                "TICKET",
                idempotencyKey,
                dto,
                HttpStatus.CREATED.value(),
                TicketResponseDto.class,
                () -> ticketService.create(dto)
        );

        return toResponseEntity(outcome);
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
    public ResponseEntity<TicketResponseDto> addLines(
            @PathVariable Long ticketId,
            @Valid @RequestBody AddTicketLinesRequestDto dto,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey
    ) {
        IdempotentOutcome<TicketResponseDto> outcome = idempotencyService.execute(
                "TICKET_ADD_LINES",
                "TICKET",
                idempotencyKey,
                Map.of("ticketId", ticketId, "body", dto),
                HttpStatus.OK.value(),
                TicketResponseDto.class,
                () -> ticketService.addLines(ticketId, dto)
        );

        return toResponseEntity(outcome);
    }

    @PostMapping("/{ticketId}/pay")
    public ResponseEntity<TicketDetailResponseDto> pay(
            @PathVariable Long ticketId,
            @Valid @RequestBody PayTicketRequestDto dto,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey
    ) {
        IdempotentOutcome<TicketDetailResponseDto> outcome = idempotencyService.execute(
                "TICKET_PAY",
                "TICKET",
                idempotencyKey,
                Map.of("ticketId", ticketId, "body", dto),
                HttpStatus.OK.value(),
                TicketDetailResponseDto.class,
                () -> ticketService.pay(ticketId, dto)
        );

        return toResponseEntity(outcome);
    }

    @PatchMapping("/{ticketId}/lines/{lineId}/cancel")
    public ResponseEntity<TicketDetailResponseDto> cancelLine(
            @PathVariable Long ticketId,
            @PathVariable Long lineId,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey
    ) {
        IdempotentOutcome<TicketDetailResponseDto> outcome = idempotencyService.execute(
                "TICKET_CANCEL_LINE",
                "TICKET",
                idempotencyKey,
                Map.of("ticketId", ticketId, "lineId", lineId),
                HttpStatus.OK.value(),
                TicketDetailResponseDto.class,
                () -> ticketService.cancelLine(ticketId, lineId)
        );

        return toResponseEntity(outcome);
    }

    @PatchMapping("/{ticketId}/cancel")
    public ResponseEntity<TicketDetailResponseDto> cancelTicket(
            @PathVariable Long ticketId,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey
    ) {
        IdempotentOutcome<TicketDetailResponseDto> outcome = idempotencyService.execute(
                "TICKET_CANCEL",
                "TICKET",
                idempotencyKey,
                Map.of("ticketId", ticketId),
                HttpStatus.OK.value(),
                TicketDetailResponseDto.class,
                () -> ticketService.cancelTicket(ticketId)
        );

        return toResponseEntity(outcome);
    }

    @PatchMapping("/{ticketId}/lines/{lineId}/discount")
    public ResponseEntity<TicketDetailResponseDto> applyLineDiscount(
            @PathVariable Long ticketId,
            @PathVariable Long lineId,
            @Valid @RequestBody ApplyLineDiscountRequestDto dto,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey
    ) {
        IdempotentOutcome<TicketDetailResponseDto> outcome = idempotencyService.execute(
                "APPLY_LINE_DISCOUNT",
                "TICKET",
                idempotencyKey,
                Map.of("ticketId", ticketId, "lineId", lineId, "body", dto),
                HttpStatus.OK.value(),
                TicketDetailResponseDto.class,
                () -> ticketService.applyLineDiscount(ticketId, lineId, dto)
        );

        return toResponseEntity(outcome);
    }

    @PatchMapping("/{ticketId}/batches/{batchNumber}/cancel")
    public ResponseEntity<TicketDetailResponseDto> cancelBatch(
            @PathVariable Long ticketId,
            @PathVariable Integer batchNumber,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey
    ) {
        IdempotentOutcome<TicketDetailResponseDto> outcome = idempotencyService.execute(
                "TICKET_CANCEL_BATCH",
                "TICKET",
                idempotencyKey,
                Map.of("ticketId", ticketId, "batchNumber", batchNumber),
                HttpStatus.OK.value(),
                TicketDetailResponseDto.class,
                () -> ticketService.cancelBatch(ticketId, batchNumber)
        );

        return toResponseEntity(outcome);
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
    public ResponseEntity<TicketDetailResponseDto> changeMesa(
            @PathVariable Long ticketId,
            @Valid @RequestBody ChangeTicketMesaRequestDto dto,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey
    ) {
        IdempotentOutcome<TicketDetailResponseDto> outcome = idempotencyService.execute(
                "TICKET_CHANGE_MESA",
                "TICKET",
                idempotencyKey,
                Map.of("ticketId", ticketId, "body", dto),
                HttpStatus.OK.value(),
                TicketDetailResponseDto.class,
                () -> ticketService.changeMesa(ticketId, dto)
        );

        return toResponseEntity(outcome);
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

    private <T> ResponseEntity<T> toResponseEntity(IdempotentOutcome<T> outcome) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(outcome.status());

        if (outcome.replayed()) {
            builder = builder.header("Idempotency-Replayed", "true");
        }

        return builder.body(outcome.body());
    }
}
