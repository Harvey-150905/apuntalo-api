package com.harbeyescala.api_apuntalo.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.math.RoundingMode;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.harbeyescala.api_apuntalo.dto.AddTicketLinesRequestDto;
import com.harbeyescala.api_apuntalo.dto.CashClosingSummaryDto;
import com.harbeyescala.api_apuntalo.dto.ChangeTicketMesaRequestDto;
import com.harbeyescala.api_apuntalo.dto.PageResponseDto;
import com.harbeyescala.api_apuntalo.dto.PayTicketRequestDto;
import com.harbeyescala.api_apuntalo.dto.PaymentMethodSummaryDto;
import com.harbeyescala.api_apuntalo.dto.TicketDetailResponseDto;
import com.harbeyescala.api_apuntalo.dto.DailySalesSummaryDto;
import com.harbeyescala.api_apuntalo.dto.TicketLineRequestDto;
import com.harbeyescala.api_apuntalo.dto.TicketLineResponseDto;
import com.harbeyescala.api_apuntalo.dto.AverageTicketSummaryDto;
import com.harbeyescala.api_apuntalo.dto.TicketRequestDto;
import com.harbeyescala.api_apuntalo.dto.TicketResponseDto;
import com.harbeyescala.api_apuntalo.dto.UpdateTicketNotesRequestDto;
import com.harbeyescala.api_apuntalo.dto.ProductSalesSummaryDto;
import com.harbeyescala.api_apuntalo.dto.UserSalesSummaryDto;
import com.harbeyescala.api_apuntalo.entity.Mesa;
import com.harbeyescala.api_apuntalo.entity.Negocio;
import com.harbeyescala.api_apuntalo.entity.Product;
import com.harbeyescala.api_apuntalo.entity.Ticket;
import com.harbeyescala.api_apuntalo.entity.TicketLine;
import com.harbeyescala.api_apuntalo.entity.User;
import com.harbeyescala.api_apuntalo.entity.enums.MesaStatus;
import com.harbeyescala.api_apuntalo.entity.enums.PaymentMethod;
import com.harbeyescala.api_apuntalo.entity.enums.TicketLineStatus;
import com.harbeyescala.api_apuntalo.entity.enums.TicketStatus;
import com.harbeyescala.api_apuntalo.exception.ResourceNotFoundException;
import com.harbeyescala.api_apuntalo.repository.MesaRepository;
import com.harbeyescala.api_apuntalo.repository.NegocioRepository;
import com.harbeyescala.api_apuntalo.repository.ProductRepository;
import com.harbeyescala.api_apuntalo.repository.TicketLineRepository;
import com.harbeyescala.api_apuntalo.repository.TicketRepository;
import com.harbeyescala.api_apuntalo.repository.UserRepository;
import com.harbeyescala.api_apuntalo.security.SecurityUtils;
import java.util.Optional;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final MesaRepository mesaRepository;
    private final NegocioRepository negocioRepository;
    private final UserRepository userRepository;
    private final TicketLineRepository ticketLineRepository;
    private final ProductRepository productRepository;

    public TicketService(
            TicketRepository ticketRepository,
            MesaRepository mesaRepository,
            NegocioRepository negocioRepository,
            UserRepository userRepository,
            TicketLineRepository ticketLineRepository,
            ProductRepository productRepository
    ) {
        this.ticketRepository = ticketRepository;
        this.mesaRepository = mesaRepository;
        this.negocioRepository = negocioRepository;
        this.userRepository = userRepository;
        this.ticketLineRepository = ticketLineRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public TicketResponseDto create(TicketRequestDto dto) {
        Long negocioId = SecurityUtils.getNegocioId();
        Long userId = SecurityUtils.getUserId();

        Mesa mesa = mesaRepository.findByIdAndNegocioId(dto.getMesaId(), negocioId)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa no encontrada"));

        if (!Boolean.TRUE.equals(mesa.getActiva())) {
            throw new IllegalStateException("La mesa está desactivada");
        }

        boolean alreadyOpen = ticketRepository.existsByMesaIdAndNegocioIdAndStatus(
                mesa.getId(),
                negocioId,
                TicketStatus.OPEN
        );

        if (alreadyOpen) {
            throw new IllegalStateException("La mesa ya tiene un ticket abierto");
        }

        Negocio negocio = negocioRepository.findById(negocioId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado"));

        User createdBy = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        Ticket ticket = Ticket.builder()
                .status(TicketStatus.OPEN)
                .total(BigDecimal.ZERO)
                .notes(normalizeNotes(dto.getNotes()))
                .mesa(mesa)
                .negocio(negocio)
                .createdBy(createdBy)
                .build();

        Ticket savedTicket = ticketRepository.save(ticket);

        mesa.setStatus(MesaStatus.OCCUPIED);
        mesaRepository.save(mesa);

        return toResponse(savedTicket);
    }

    public TicketResponseDto findOpenByMesa(Long mesaId) {
        Long negocioId = SecurityUtils.getNegocioId();

        mesaRepository.findByIdAndNegocioId(mesaId, negocioId)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa no encontrada"));

        Ticket ticket = ticketRepository.findByMesaIdAndNegocioIdAndStatus(
                        mesaId,
                        negocioId,
                        TicketStatus.OPEN
                )
                .orElseThrow(() -> new ResourceNotFoundException("La mesa no tiene ticket abierto"));

        return toResponse(ticket);
    }

    @Transactional
    public TicketResponseDto addLines(Long ticketId, AddTicketLinesRequestDto dto) {
        Long negocioId = SecurityUtils.getNegocioId();

        Ticket ticket = ticketRepository.findByIdAndNegocioId(ticketId, negocioId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket no encontrado"));

        ensureTicketOpen(ticket);

        Integer lastBatch = ticketLineRepository.findTopByTicketIdOrderByBatchNumberDesc(ticketId)
                .map(TicketLine::getBatchNumber)
                .orElse(0);

        int newBatch = lastBatch + 1;

        Map<String, GroupedLine> grouped = new LinkedHashMap<>();

        for (TicketLineRequestDto lineDto : dto.getLines()) {
            String normalizedNotes = normalizeNotes(lineDto.getNotes());
            Product product = productRepository.findByIdAndNegocioId(lineDto.getProductId(), negocioId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Producto no encontrado: " + lineDto.getProductId()
                    ));

            if (!Boolean.TRUE.equals(product.getActivo())) {
                throw new IllegalStateException("El producto está inactivo: " + product.getName());
            }

            String key = buildGroupKey(product.getId(), normalizedNotes);

           if (grouped.containsKey(key)) {
                GroupedLine existing = grouped.get(key);
                int newQuantity = existing.getQuantity() + lineDto.getQuantity();

                if (newQuantity > 100) {
                    throw new IllegalStateException("La cantidad total para un mismo producto no puede ser mayor que 100");
                }

                existing.setQuantity(newQuantity);
            } else {
                grouped.put(key, new GroupedLine(
                        product.getId(),
                        product.getName(),
                        product.getPrice(),
                        lineDto.getQuantity(),
                        normalizedNotes
                ));
            }
        }

        for (GroupedLine groupedLine : grouped.values()) {
            BigDecimal subtotal = groupedLine.getUnitPrice()
                    .multiply(BigDecimal.valueOf(groupedLine.getQuantity()));

            TicketLine ticketLine = TicketLine.builder()
                    .ticket(ticket)
                    .productId(groupedLine.getProductId())
                    .productNameSnapshot(groupedLine.getProductName())
                    .unitPriceSnapshot(groupedLine.getUnitPrice())
                    .quantity(groupedLine.getQuantity())
                    .subtotal(subtotal)
                    .batchNumber(newBatch)
                    .status(TicketLineStatus.ACTIVE)
                    .notes(groupedLine.getNotes())
                    .build();

            ticketLineRepository.save(ticketLine);
        }

        recalculateTicketTotal(ticket);

        return toResponse(ticketRepository.save(ticket));
    }

    @Transactional
    public TicketDetailResponseDto pay(Long ticketId, PayTicketRequestDto dto) {
        Long negocioId = SecurityUtils.getNegocioId();
        Long userId = SecurityUtils.getUserId();

        Ticket ticket = ticketRepository.findByIdAndNegocioId(ticketId, negocioId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket no encontrado"));

        if (ticket.getStatus() != TicketStatus.OPEN) {
            throw new IllegalStateException("Solo se pueden pagar tickets abiertos");
        }

        List<TicketLine> activeLines = getActiveLinesOrThrow(ticket);

        User paidBy = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        recalculateTicketTotal(ticket);

        ticket.setStatus(TicketStatus.PAID);
        ticket.setPaidAt(LocalDateTime.now());
        ticket.setPaidBy(paidBy);
        ticket.setPaymentMethod(dto.getPaymentMethod());

        ticketRepository.save(ticket);

        Mesa mesa = ticket.getMesa();
        if (mesa != null) {
            mesa.setStatus(MesaStatus.FREE);
            mesaRepository.save(mesa);
        }

        List<TicketLineResponseDto> lines = activeLines.stream()
                .map(this::toLineResponse)
                .toList();

        return toDetailResponse(ticket, lines);
    }

    @Transactional
    public TicketDetailResponseDto cancelLine(Long ticketId, Long lineId) {
        Long negocioId = SecurityUtils.getNegocioId();

        Ticket ticket = ticketRepository.findByIdAndNegocioId(ticketId, negocioId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket no encontrado"));

        ensureTicketOpen(ticket);

        TicketLine line = ticketLineRepository
                .findByIdAndTicketIdAndTicketNegocioId(lineId, ticketId, negocioId)
                .orElseThrow(() -> new ResourceNotFoundException("Línea no encontrada"));

        if (!line.getTicket().getId().equals(ticket.getId())) {
            throw new ResourceNotFoundException("La línea no pertenece al ticket indicado");
        }

        if (!line.getTicket().getNegocio().getId().equals(negocioId)) {
            throw new ResourceNotFoundException("Línea no encontrada");
        }

        if (line.getStatus() == TicketLineStatus.CANCELLED) {
            throw new IllegalStateException("La línea ya está cancelada");
        }

        line.setStatus(TicketLineStatus.CANCELLED);
        ticketLineRepository.save(line);

        recalculateTicketTotal(ticket);
        ticketRepository.save(ticket);

        List<TicketLineResponseDto> lines = ticketLineRepository
                .findByTicketIdOrderByCreatedAtAsc(ticket.getId())
                .stream()
                .map(this::toLineResponse)
                .toList();

        return toDetailResponse(ticket, lines);
    }

    @Transactional
    public TicketDetailResponseDto cancelBatch(Long ticketId, Integer batchNumber) {
        Long negocioId = SecurityUtils.getNegocioId();

        Ticket ticket = ticketRepository.findByIdAndNegocioId(ticketId, negocioId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket no encontrado"));

        ensureTicketOpen(ticket);

        List<TicketLine> lines = ticketLineRepository
            .findByTicketIdAndBatchNumberAndTicketNegocioId(ticketId, batchNumber, negocioId);

        if (lines.isEmpty()) {
            throw new ResourceNotFoundException("Batch no encontrado");
        }

        for (TicketLine line : lines) {
            if (line.getStatus() == TicketLineStatus.ACTIVE) {
                line.setStatus(TicketLineStatus.CANCELLED);
            }
        }

        ticketLineRepository.saveAll(lines);

        recalculateTicketTotal(ticket);
        ticketRepository.save(ticket);

        List<TicketLineResponseDto> allLines = ticketLineRepository
                .findByTicketIdOrderByCreatedAtAsc(ticket.getId())
                .stream()
                .map(this::toLineResponse)
                .toList();

        return toDetailResponse(ticket, allLines);
    }

    @Transactional
    public TicketDetailResponseDto cancelTicket(Long ticketId) {
        Long negocioId = SecurityUtils.getNegocioId();

        Ticket ticket = ticketRepository.findByIdAndNegocioId(ticketId, negocioId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket no encontrado"));

        ensureTicketOpen(ticket);

        List<TicketLine> lines = ticketLineRepository
            .findByTicketIdAndTicketNegocioIdOrderByCreatedAtAsc(ticket.getId(), negocioId);

        for (TicketLine line : lines) {
            if (line.getStatus() == TicketLineStatus.ACTIVE) {
                line.setStatus(TicketLineStatus.CANCELLED);
            }
        }

        ticketLineRepository.saveAll(lines);

        recalculateTicketTotal(ticket); // quedará en 0

        ticket.setStatus(TicketStatus.CANCELLED);
        ticketRepository.save(ticket);

        Mesa mesa = ticket.getMesa();
        mesa.setStatus(MesaStatus.FREE);
        mesaRepository.save(mesa);

        List<TicketLineResponseDto> responseLines = lines.stream()
                .map(this::toLineResponse)
                .toList();

        return toDetailResponse(ticket, responseLines);
    }

    public PaymentMethodSummaryDto getPaymentMethodSummary(LocalDate from, LocalDate to) {
        Long negocioId = SecurityUtils.getNegocioId();

        if (from == null || to == null) {
            throw new IllegalStateException("Debes enviar ambas fechas");
        }

        if (from.isAfter(to)) {
            throw new IllegalStateException("La fecha 'from' no puede ser mayor que 'to'");
        }

        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.atTime(23, 59, 59);

        BigDecimal cashTotal = ticketRepository
                .sumTotalByNegocioIdAndStatusAndPaymentMethodAndPaidAtBetween(
                        negocioId,
                        TicketStatus.PAID,
                        PaymentMethod.CASH,
                        fromDateTime,
                        toDateTime
                );

        BigDecimal cardTotal = ticketRepository
                .sumTotalByNegocioIdAndStatusAndPaymentMethodAndPaidAtBetween(
                        negocioId,
                        TicketStatus.PAID,
                        PaymentMethod.CARD,
                        fromDateTime,
                        toDateTime
                );

        BigDecimal totalSales = ticketRepository
            .sumTotalByNegocioIdAndStatusAndPaidAtBetween(
                    negocioId,
                    TicketStatus.PAID,
                    fromDateTime,
                    toDateTime
            );

        return new PaymentMethodSummaryDto(
                cashTotal != null ? cashTotal : BigDecimal.ZERO,
                cardTotal != null ? cardTotal : BigDecimal.ZERO,
                totalSales != null ? totalSales : BigDecimal.ZERO
        );
    }
    
    public TicketDetailResponseDto findDetailById(Long ticketId) {
        Long negocioId = SecurityUtils.getNegocioId();

        Ticket ticket = ticketRepository.findByIdAndNegocioId(ticketId, negocioId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket no encontrado"));

        List<TicketLineResponseDto> lines = ticketLineRepository
            .findByTicketIdAndTicketNegocioIdOrderByCreatedAtAsc(ticket.getId(), negocioId)
            .stream()
            .map(this::toLineResponse)
            .toList();

        return toDetailResponse(ticket, lines);
    }

    public List<TicketResponseDto> findPaidTicketsByDateRange(LocalDate from, LocalDate to) {
        Long negocioId = SecurityUtils.getNegocioId();

        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.atTime(23, 59, 59);

        return ticketRepository
                .findByNegocioIdAndStatusAndPaidAtBetweenOrderByPaidAtDesc(
                        negocioId,
                        TicketStatus.PAID,
                        fromDateTime,
                        toDateTime
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<UserSalesSummaryDto> getUserSalesSummary(LocalDate from, LocalDate to) {
        Long negocioId = SecurityUtils.getNegocioId();

        if (from == null || to == null) {
            throw new IllegalStateException("Debes enviar ambas fechas");
        }

        if (from.isAfter(to)) {
            throw new IllegalStateException("La fecha 'from' no puede ser mayor que 'to'");
        }

        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.atTime(23, 59, 59);

        return ticketRepository.findUserSalesSummaryByNegocioIdAndStatusAndPaidAtBetween(
                negocioId,
                TicketStatus.PAID,
                fromDateTime,
                toDateTime
        );
    }
    public List<TicketResponseDto> findPaidTicketsFiltered(LocalDate from, LocalDate to) {
        if ((from == null && to != null) || (from != null && to == null)) {
            throw new IllegalStateException("Debes enviar ambas fechas: from y to");
        }

        if (from != null && from.isAfter(to)) {
            throw new IllegalStateException("La fecha 'from' no puede ser mayor que 'to'");
        }

        if (from != null) {
            return findPaidTicketsByDateRange(from, to);
        }

        return findPaidTickets();
    }

    public List<TicketResponseDto> findOpenTickets() {
        Long negocioId = SecurityUtils.getNegocioId();

        return ticketRepository.findByNegocioIdAndStatusOrderByCreatedAtDesc(negocioId, TicketStatus.OPEN)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<TicketResponseDto> findPaidTickets() {
        Long negocioId = SecurityUtils.getNegocioId();

        return ticketRepository.findByNegocioIdAndStatusOrderByPaidAtDesc(negocioId, TicketStatus.PAID)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    public List<TicketResponseDto> findCancelledTickets() {
        Long negocioId = SecurityUtils.getNegocioId();

        return ticketRepository
                .findByNegocioIdAndStatusOrderByUpdatedAtDesc(negocioId, TicketStatus.CANCELLED)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public BigDecimal getTotalSales(LocalDate from, LocalDate to) {
        Long negocioId = SecurityUtils.getNegocioId();

        if (from == null || to == null) {
            throw new IllegalStateException("Debes enviar ambas fechas");
        }

        if (from.isAfter(to)) {
            throw new IllegalStateException("La fecha 'from' no puede ser mayor que 'to'");
        }

        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.atTime(23, 59, 59);

        BigDecimal total = ticketRepository
                .sumTotalByNegocioIdAndStatusAndPaidAtBetween(
                        negocioId,
                        TicketStatus.PAID,
                        fromDateTime,
                        toDateTime
                );

        return total != null ? total : BigDecimal.ZERO;
    }

    public CashClosingSummaryDto getCashClosingSummary(LocalDate from, LocalDate to) {
        Long negocioId = SecurityUtils.getNegocioId();

        if (from == null || to == null) {
            throw new IllegalStateException("Debes enviar ambas fechas");
        }

        if (from.isAfter(to)) {
            throw new IllegalStateException("La fecha 'from' no puede ser mayor que 'to'");
        }

        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.atTime(23, 59, 59);

        BigDecimal totalSales = ticketRepository.sumTotalByNegocioIdAndStatusAndPaidAtBetween(
                negocioId,
                TicketStatus.PAID,
                fromDateTime,
                toDateTime
        );

        BigDecimal cashTotal = ticketRepository.sumTotalByNegocioIdAndStatusAndPaymentMethodAndPaidAtBetween(
                negocioId,
                TicketStatus.PAID,
                PaymentMethod.CASH,
                fromDateTime,
                toDateTime
        );

        BigDecimal cardTotal = ticketRepository.sumTotalByNegocioIdAndStatusAndPaymentMethodAndPaidAtBetween(
                negocioId,
                TicketStatus.PAID,
                PaymentMethod.CARD,
                fromDateTime,
                toDateTime
        );

        Long paidTickets = ticketRepository.countByNegocioIdAndStatusAndPaidAtBetween(
                negocioId,
                TicketStatus.PAID,
                fromDateTime,
                toDateTime
        );

        Long cancelledTickets = ticketRepository.countByNegocioIdAndStatusAndUpdatedAtBetween(
                negocioId,
                TicketStatus.CANCELLED,
                fromDateTime,
                toDateTime
        );

        return new CashClosingSummaryDto(
                totalSales != null ? totalSales : BigDecimal.ZERO,
                cashTotal != null ? cashTotal : BigDecimal.ZERO,
                cardTotal != null ? cardTotal : BigDecimal.ZERO,
                paidTickets != null ? paidTickets : 0L,
                cancelledTickets != null ? cancelledTickets : 0L
        );
    }

    private void recalculateTicketTotal(Ticket ticket) {
        List<TicketLine> activeLines = getActiveLinesOrThrow(ticket);

        BigDecimal total = activeLines.stream()
                .map(TicketLine::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        ticket.setTotal(total);
    }

    public PageResponseDto<TicketResponseDto> findOpenTicketsPaged(int page, int size) {
        Long negocioId = SecurityUtils.getNegocioId();

        Pageable pageable = PageRequest.of(page, size);

        Page<Ticket> ticketPage = ticketRepository
            .findByNegocioIdAndStatusOrderByCreatedAtDesc(
                negocioId,
                TicketStatus.OPEN,
                pageable
            );

        List<TicketResponseDto> content = ticketPage.getContent()
            .stream()
            .map(this::toResponse)
            .toList();

        return new PageResponseDto<>(
            content,
            ticketPage.getNumber(),
            ticketPage.getSize(),
            ticketPage.getTotalElements(),
            ticketPage.getTotalPages(),
            ticketPage.isLast()
        );
    }

    public PageResponseDto<TicketResponseDto> findCancelledTicketsPaged(int page, int size) {
        Long negocioId = SecurityUtils.getNegocioId();

        Pageable pageable = PageRequest.of(page, size);

        Page<Ticket> ticketPage = ticketRepository
            .findByNegocioIdAndStatusOrderByUpdatedAtDesc(
                negocioId,
                TicketStatus.CANCELLED,
                pageable
            );

        List<TicketResponseDto> content = ticketPage.getContent()
            .stream()
            .map(this::toResponse)
            .toList();

        return new PageResponseDto<>(
            content,
            ticketPage.getNumber(),
            ticketPage.getSize(),
            ticketPage.getTotalElements(),
            ticketPage.getTotalPages(),
            ticketPage.isLast()
        );
    }

    public List<ProductSalesSummaryDto> getProductSalesSummary(LocalDate from, LocalDate to) {
        Long negocioId = SecurityUtils.getNegocioId();

        validateDateRange(from, to);

        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.atTime(23, 59, 59);

        return ticketLineRepository.findProductSalesSummary(
                negocioId,
                TicketStatus.PAID,
                TicketLineStatus.ACTIVE,
                fromDateTime,
                toDateTime
        );
    }
    public List<DailySalesSummaryDto> getDailySalesSummary(LocalDate from, LocalDate to) {
        Long negocioId = SecurityUtils.getNegocioId();

        validateDateRange(from, to);

        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.atTime(23, 59, 59);

        List<Ticket> paidTickets = ticketRepository
                .findByNegocioIdAndStatusAndPaidAtBetweenOrderByPaidAtDesc(
                        negocioId,
                        TicketStatus.PAID,
                        fromDateTime,
                        toDateTime
                );

        Map<LocalDate, DailyAccumulator> dailyMap = new LinkedHashMap<>();

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            dailyMap.put(date, new DailyAccumulator());
        }

        for (Ticket ticket : paidTickets) {
            if (ticket.getPaidAt() == null) {
                continue;
            }

            LocalDate day = ticket.getPaidAt().toLocalDate();
            DailyAccumulator acc = dailyMap.get(day);

            if (acc != null) {
                acc.ticketCount++;
                acc.totalSales = acc.totalSales.add(ticket.getTotal() != null ? ticket.getTotal() : BigDecimal.ZERO);
            }
        }

        return dailyMap.entrySet().stream()
                .map(entry -> new DailySalesSummaryDto(
                        entry.getKey(),
                        entry.getValue().ticketCount,
                        entry.getValue().totalSales
                ))
                .toList();
    }
    public AverageTicketSummaryDto getAverageTicketSummary(LocalDate from, LocalDate to) {
        Long negocioId = SecurityUtils.getNegocioId();

        validateDateRange(from, to);

        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.atTime(23, 59, 59);

        BigDecimal totalSales = ticketRepository.sumTotalByNegocioIdAndStatusAndPaidAtBetween(
                negocioId,
                TicketStatus.PAID,
                fromDateTime,
                toDateTime
        );

        Long ticketCount = ticketRepository.countByNegocioIdAndStatusAndPaidAtBetween(
                negocioId,
                TicketStatus.PAID,
                fromDateTime,
                toDateTime
        );

        BigDecimal safeTotalSales = totalSales != null ? totalSales : BigDecimal.ZERO;
        long safeTicketCount = ticketCount != null ? ticketCount : 0L;

        BigDecimal averageTicket = safeTicketCount == 0
                ? BigDecimal.ZERO
                : safeTotalSales.divide(BigDecimal.valueOf(safeTicketCount), 2, RoundingMode.HALF_UP);

        return new AverageTicketSummaryDto(
                safeTicketCount,
                safeTotalSales,
                averageTicket
        );
    }

    @Transactional
    public TicketDetailResponseDto updateNotes(Long ticketId, UpdateTicketNotesRequestDto dto) {
        Long negocioId = SecurityUtils.getNegocioId();

        Ticket ticket = ticketRepository.findByIdAndNegocioId(ticketId, negocioId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket no encontrado"));

        ensureTicketOpen(ticket);

        ticket.setNotes(normalizeNotes(dto.getNotes()));
        ticketRepository.save(ticket);

        List<TicketLineResponseDto> lines = ticketLineRepository
                .findByTicketIdAndTicketNegocioIdOrderByCreatedAtAsc(ticket.getId(), negocioId)
                .stream()
                .map(this::toLineResponse)
                .toList();

        return toDetailResponse(ticket, lines);
    }

        public PageResponseDto<TicketResponseDto> findPaidTicketsPaged(
        LocalDate from,
        LocalDate to,
        int page,
        int size
    ) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Las fechas from y to son obligatorias");
        }

        if (from.isAfter(to)) {
            throw new IllegalArgumentException("La fecha from no puede ser posterior a to");
        }

        Long negocioId = SecurityUtils.getNegocioId();

        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.atTime(23, 59, 59);

        Pageable pageable = PageRequest.of(page, size);

        Page<Ticket> ticketPage = ticketRepository
            .findByNegocioIdAndStatusAndPaidAtBetweenOrderByPaidAtDesc(
                negocioId,
                TicketStatus.PAID,
                fromDateTime,
                toDateTime,
                pageable
            );

        List<TicketResponseDto> content = ticketPage.getContent()
            .stream()
            .map(this::toResponse)
            .toList();

        return new PageResponseDto<>(
            content,
            ticketPage.getNumber(),
            ticketPage.getSize(),
            ticketPage.getTotalElements(),
            ticketPage.getTotalPages(),
            ticketPage.isLast()
        );
    }

    @Transactional
    public TicketDetailResponseDto changeMesa(Long ticketId, ChangeTicketMesaRequestDto dto) {
        Long negocioId = SecurityUtils.getNegocioId();

        Ticket ticket = ticketRepository.findByIdAndNegocioId(ticketId, negocioId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket no encontrado"));

        ensureTicketOpen(ticket);

        Mesa nuevaMesa = mesaRepository.findByIdAndNegocioId(dto.getMesaId(), negocioId)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa no encontrada"));

        if (!Boolean.TRUE.equals(nuevaMesa.getActiva())) {
            throw new IllegalStateException("No se puede mover el ticket a una mesa inactiva");
        }

        if (ticket.getMesa() != null && ticket.getMesa().getId().equals(nuevaMesa.getId())) {
            throw new IllegalStateException("El ticket ya está asignado a esa mesa");
        }

        Optional<Ticket> existingOpenTicket = ticketRepository
                .findByMesaIdAndNegocioIdAndStatus(dto.getMesaId(), negocioId, TicketStatus.OPEN);

        if (existingOpenTicket.isPresent() && !existingOpenTicket.get().getId().equals(ticket.getId())) {
            throw new IllegalStateException("La mesa seleccionada ya tiene un ticket abierto");
        }

        Mesa mesaAnterior = ticket.getMesa();

        ticket.setMesa(nuevaMesa);
        ticketRepository.save(ticket);

        if (mesaAnterior != null && !mesaAnterior.getId().equals(nuevaMesa.getId())) {
            mesaAnterior.setStatus(MesaStatus.FREE);
            mesaRepository.save(mesaAnterior);
        }

        // Ocupar nueva mesa
        nuevaMesa.setStatus(MesaStatus.OCCUPIED);
        mesaRepository.save(nuevaMesa);

        List<TicketLineResponseDto> lines = ticketLineRepository
                .findByTicketIdAndTicketNegocioIdOrderByCreatedAtAsc(ticket.getId(), negocioId)
                .stream()
                .map(this::toLineResponse)
                .toList();

        return toDetailResponse(ticket, lines);
    }

        private String buildGroupKey(Long productId, String notes) {
        return productId + "::" + (notes == null ? "" : notes.trim().toLowerCase());
    }

    private TicketResponseDto toResponse(Ticket ticket) {
        return TicketResponseDto.builder()
                .id(ticket.getId())
                .status(ticket.getStatus())
                .total(ticket.getTotal())
                .notes(ticket.getNotes())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .paidAt(ticket.getPaidAt())
                .mesaId(ticket.getMesa().getId())
                .mesaNumero(ticket.getMesa().getNumero())
                .createdById(ticket.getCreatedBy().getId())
                .createdByUsername(ticket.getCreatedBy().getUsername())
                .paidById(ticket.getPaidBy() != null ? ticket.getPaidBy().getId() : null)
                .paidByUsername(ticket.getPaidBy() != null ? ticket.getPaidBy().getUsername() : null)
                .paymentMethod(ticket.getPaymentMethod())
                .build();
    }

    private void ensureTicketOpen(Ticket ticket) {
        if (ticket.getStatus() != TicketStatus.OPEN) {
            throw new IllegalStateException("Solo se pueden modificar tickets abiertos");
        }
    }

    private List<TicketLine> getActiveLinesOrThrow(Ticket ticket) {
        List<TicketLine> activeLines = ticketLineRepository.findByTicketIdAndStatusOrderByCreatedAtAsc(
                ticket.getId(),
                TicketLineStatus.ACTIVE
        );

        if (activeLines.isEmpty()) {
            throw new IllegalStateException("No se puede pagar un ticket vacío");
        }

        return activeLines;
    }

    private String normalizeNotes(String notes) {
        if (notes == null) {
            return null;
        }

        String normalized = notes.trim();

        if (normalized.isEmpty()) {
            return null;
        }

        return normalized;
    }

    private static class GroupedLine {
        private final Long productId;
        private final String productName;
        private final BigDecimal unitPrice;
        private Integer quantity;
        private final String notes;

        public GroupedLine(Long productId, String productName, BigDecimal unitPrice, Integer quantity, String notes) {
            this.productId = productId;
            this.productName = productName;
            this.unitPrice = unitPrice;
            this.quantity = quantity;
            this.notes = notes;
        }

        public Long getProductId() {
            return productId;
        }

        public String getProductName() {
            return productName;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public String getNotes() {
            return notes;
        }
    }

    private TicketDetailResponseDto toDetailResponse(Ticket ticket, List<TicketLineResponseDto> lines) {
        return TicketDetailResponseDto.builder()
                .id(ticket.getId())
                .status(ticket.getStatus())
                .total(ticket.getTotal())
                .notes(ticket.getNotes())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .paidAt(ticket.getPaidAt())
                .mesaId(ticket.getMesa().getId())
                .mesaNumero(ticket.getMesa().getNumero())
                .createdById(ticket.getCreatedBy().getId())
                .createdByUsername(ticket.getCreatedBy().getUsername())
                .paidById(ticket.getPaidBy() != null ? ticket.getPaidBy().getId() : null)
                .paidByUsername(ticket.getPaidBy() != null ? ticket.getPaidBy().getUsername() : null)
                .lines(lines)
                .paymentMethod(ticket.getPaymentMethod())
                .build();
    }

    private TicketLineResponseDto toLineResponse(TicketLine line) {
        return TicketLineResponseDto.builder()
                .id(line.getId())
                .productId(line.getProductId())
                .productNameSnapshot(line.getProductNameSnapshot())
                .unitPriceSnapshot(line.getUnitPriceSnapshot())
                .quantity(line.getQuantity())
                .subtotal(line.getSubtotal())
                .batchNumber(line.getBatchNumber())
                .status(line.getStatus())
                .notes(line.getNotes())
                .createdAt(line.getCreatedAt())
                .build();
    }
    
    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalStateException("Debes enviar ambas fechas");
        }

        if (from.isAfter(to)) {
            throw new IllegalStateException("La fecha 'from' no puede ser mayor que 'to'");
        }
    }
    private static class DailyAccumulator {
        private long ticketCount = 0L;
        private BigDecimal totalSales = BigDecimal.ZERO;
    }
}