package com.harbeyescala.api_apuntalo.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.math.RoundingMode;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.harbeyescala.api_apuntalo.dto.AddTicketLinesRequestDto;
import com.harbeyescala.api_apuntalo.dto.ApplyLineDiscountRequestDto;
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
import com.harbeyescala.api_apuntalo.entity.TicketNumberSequence;
import com.harbeyescala.api_apuntalo.entity.User;
import com.harbeyescala.api_apuntalo.entity.enums.AuditAction;
import com.harbeyescala.api_apuntalo.entity.enums.AuditEntityType;
import com.harbeyescala.api_apuntalo.entity.enums.MesaStatus;
import com.harbeyescala.api_apuntalo.entity.enums.PaymentMethod;
import com.harbeyescala.api_apuntalo.entity.enums.TicketLineStatus;
import com.harbeyescala.api_apuntalo.entity.enums.TicketStatus;
import com.harbeyescala.api_apuntalo.exception.BusinessRuleException;
import com.harbeyescala.api_apuntalo.exception.ConflictException;
import com.harbeyescala.api_apuntalo.exception.ResourceNotFoundException;
import com.harbeyescala.api_apuntalo.repository.MesaRepository;
import com.harbeyescala.api_apuntalo.repository.NegocioRepository;
import com.harbeyescala.api_apuntalo.repository.ProductRepository;
import com.harbeyescala.api_apuntalo.repository.TicketLineRepository;
import com.harbeyescala.api_apuntalo.repository.TicketNumberSequenceRepository;
import com.harbeyescala.api_apuntalo.repository.TicketRepository;
import com.harbeyescala.api_apuntalo.repository.UserRepository;
import com.harbeyescala.api_apuntalo.security.CurrentUser;
import java.util.Optional;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final MesaRepository mesaRepository;
    private final NegocioRepository negocioRepository;
    private final UserRepository userRepository;
    private final TicketLineRepository ticketLineRepository;
    private final ProductRepository productRepository;
    private final TicketNumberSequenceRepository ticketNumberSequenceRepository;
    private final TicketLinePricingService ticketLinePricingService;
    private final AuditEventService auditEventService;
    private final AuditFailureRecorder auditFailureRecorder;
    private final CurrentUser currentUser;

    public TicketService(
            TicketRepository ticketRepository,
            MesaRepository mesaRepository,
            NegocioRepository negocioRepository,
            UserRepository userRepository,
            TicketLineRepository ticketLineRepository,
            ProductRepository productRepository,
            TicketNumberSequenceRepository ticketNumberSequenceRepository,
            TicketLinePricingService ticketLinePricingService,
            AuditEventService auditEventService,
            AuditFailureRecorder auditFailureRecorder,
            CurrentUser currentUser
    ) {
        this.ticketRepository = ticketRepository;
        this.mesaRepository = mesaRepository;
        this.negocioRepository = negocioRepository;
        this.userRepository = userRepository;
        this.ticketLineRepository = ticketLineRepository;
        this.productRepository = productRepository;
        this.ticketNumberSequenceRepository = ticketNumberSequenceRepository;
        this.ticketLinePricingService = ticketLinePricingService;
        this.auditEventService = auditEventService;
        this.auditFailureRecorder = auditFailureRecorder;
        this.currentUser = currentUser;
    }

    // ------------------------------------------------------------------
    // Creación
    // ------------------------------------------------------------------

    @Transactional
    public TicketResponseDto create(TicketRequestDto dto) {
        Long tenantId = currentUser.getTenantId();
        Long userId = currentUser.getUserId();

        // Bloqueo de mesa: comprobar-y-ocupar debe ser atómico (Fase 3.4).
        Mesa mesa = mesaRepository.findByIdAndNegocioIdForUpdate(dto.getMesaId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa no encontrada"));

        try {
            if (!Boolean.TRUE.equals(mesa.getActiva())) {
                throw new BusinessRuleException("MESA_INACTIVE", "La mesa está desactivada");
            }

            boolean alreadyOpen = ticketRepository.existsByMesaIdAndNegocioIdAndStatus(
                    mesa.getId(),
                    tenantId,
                    TicketStatus.OPEN
            );

            if (alreadyOpen) {
                throw new ConflictException("TABLE_ALREADY_OCCUPIED", "La mesa ya tiene un ticket abierto");
            }
        } catch (BusinessRuleException | ConflictException ex) {
            auditFailureSafely(AuditEntityType.TICKET, null, AuditAction.TICKET_CREATED, ex.getCode());
            throw ex;
        }

        Negocio negocio = negocioRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado"));

        User createdBy = userRepository.findByIdAndNegocioId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        Ticket ticket = Ticket.builder()
                .status(TicketStatus.OPEN)
                .total(BigDecimal.ZERO)
                .notes(normalizeNotes(dto.getNotes()))
                .mesa(mesa)
                .negocio(negocio)
                .createdBy(createdBy)
                .build();

        // La restricción única parcial "uk_ticket_mesa_open" (Postgres) es la
        // última línea de defensa si dos transacciones llegan a coincidir.
        Ticket savedTicket = ticketRepository.save(ticket);

        mesa.setStatus(MesaStatus.OCCUPIED);
        mesaRepository.save(mesa);

        auditEventService.recordSuccess(
                AuditEntityType.TICKET,
                savedTicket.getId(),
                AuditAction.TICKET_CREATED,
                null,
                ticketSnapshot(savedTicket)
        );

        return toResponse(savedTicket);
    }

    public TicketResponseDto findOpenByMesa(Long mesaId) {
        Long tenantId = currentUser.getTenantId();

        mesaRepository.findByIdAndNegocioId(mesaId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa no encontrada"));

        Ticket ticket = ticketRepository.findByMesaIdAndNegocioIdAndStatus(
                        mesaId,
                        tenantId,
                        TicketStatus.OPEN
                )
                .orElseThrow(() -> new ResourceNotFoundException("La mesa no tiene ticket abierto"));

        return toResponse(ticket);
    }

    // ------------------------------------------------------------------
    // Añadir líneas
    // ------------------------------------------------------------------

    @Transactional
    public TicketResponseDto addLines(Long ticketId, AddTicketLinesRequestDto dto) {
        Long tenantId = currentUser.getTenantId();

        // Bloqueo del ticket: serializa el cálculo del batch y del total
        // frente a otras tablets añadiendo líneas al mismo ticket (Fase 3.7/3.8).
        Ticket ticket = ticketRepository.findByIdAndNegocioIdForUpdate(ticketId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket no encontrado"));

        try {
            ensureOpen(ticket);

            Integer lastBatch = ticketLineRepository.findTopByTicketIdOrderByBatchNumberDesc(ticketId)
                    .map(TicketLine::getBatchNumber)
                    .orElse(0);

            int newBatch = lastBatch + 1;

            Map<String, GroupedLine> grouped = new LinkedHashMap<>();

            for (TicketLineRequestDto lineDto : dto.getLines()) {
                String normalizedNotes = normalizeNotes(lineDto.getNotes());
                Product product = productRepository.findByIdAndNegocioId(lineDto.getProductId(), tenantId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Producto no encontrado: " + lineDto.getProductId()
                        ));

                if (!Boolean.TRUE.equals(product.getActivo())) {
                    throw new BusinessRuleException("PRODUCT_INACTIVE", "El producto está inactivo: " + product.getName());
                }

                String key = buildGroupKey(product.getId(), normalizedNotes);

               if (grouped.containsKey(key)) {
                    GroupedLine existing = grouped.get(key);
                    int newQuantity = existing.getQuantity() + lineDto.getQuantity();

                    if (newQuantity > 100) {
                        throw new BusinessRuleException("INVALID_QUANTITY", "La cantidad total para un mismo producto no puede ser mayor que 100");
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
                        .subtotalBeforeDiscount(subtotal)
                        .discountPercentage(0)
                        .discountAmount(BigDecimal.ZERO)
                        .batchNumber(newBatch)
                        .status(TicketLineStatus.ACTIVE)
                        .notes(groupedLine.getNotes())
                        .build();

                ticketLineRepository.save(ticketLine);
            }
        } catch (BusinessRuleException | ConflictException ex) {
            auditFailureSafely(AuditEntityType.TICKET, ticketId, AuditAction.TICKET_LINES_ADDED, ex.getCode());
            throw ex;
        }

        recalculateTicketTotal(ticket);

        Ticket savedTicket = ticketRepository.save(ticket);

        auditEventService.recordSuccess(
                AuditEntityType.TICKET,
                ticket.getId(),
                AuditAction.TICKET_LINES_ADDED,
                null,
                ticketSnapshot(savedTicket),
                Map.of("addedLineGroups", dto.getLines().size())
        );

        return toResponse(savedTicket);
    }

    // ------------------------------------------------------------------
    // Pago
    // ------------------------------------------------------------------

    @Transactional
    public TicketDetailResponseDto pay(Long ticketId, PayTicketRequestDto dto) {
        Long tenantId = currentUser.getTenantId();
        Long userId = currentUser.getUserId();

        // Bloqueo de escritura antes de comprobar el estado (Fase 3.6): la
        // segunda petición concurrente espera a que la primera termine y
        // encuentra el ticket ya PAID, sin volver a cobrar.
        Ticket ticket = ticketRepository.findByIdAndNegocioIdForUpdate(ticketId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket no encontrado"));

        Map<String, Object> previousState = ticketSnapshot(ticket);
        List<TicketLine> activeLines;

        try {
            ensureOpen(ticket);

            activeLines = ticketLineRepository.findByTicketIdAndStatusOrderByCreatedAtAsc(
                    ticket.getId(),
                    TicketLineStatus.ACTIVE
            );

            if (activeLines.isEmpty()) {
                throw new BusinessRuleException("TICKET_EMPTY", "No se puede pagar un ticket sin líneas activas");
            }

            BigDecimal total = sumActive(activeLines);
            ticket.setTotal(total);

            if (total.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessRuleException("TICKET_EMPTY", "El total del ticket debe ser mayor que cero");
            }
        } catch (BusinessRuleException | ConflictException ex) {
            auditFailureSafely(AuditEntityType.TICKET, ticketId, AuditAction.TICKET_PAID, ex.getCode());
            throw ex;
        }

        User paidBy = userRepository.findByIdAndNegocioId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        ticket.setStatus(TicketStatus.PAID);
        ticket.setPaidAt(LocalDateTime.now());
        ticket.setPaidBy(paidBy);
        ticket.setPaymentMethod(dto.getPaymentMethod());

        // Numeración comercial bajo bloqueo (Fase 5.1): la secuencia por
        // negocio se bloquea con PESSIMISTIC_WRITE y se incrementa de forma
        // atómica junto al resto del pago, dentro de la misma transacción
        // que ya tiene el ticket bloqueado.
        Long assignedNumber = assignCommercialNumber(ticket, tenantId);

        ticketRepository.save(ticket);

        freeMesa(ticket, tenantId);

        List<TicketLineResponseDto> lines = activeLines.stream()
                .map(this::toLineResponse)
                .toList();

        auditEventService.recordSuccess(
                AuditEntityType.TICKET,
                ticket.getId(),
                AuditAction.TICKET_PAID,
                previousState,
                ticketSnapshot(ticket)
        );
        auditEventService.recordSuccess(
                AuditEntityType.TICKET,
                ticket.getId(),
                AuditAction.COMMERCIAL_NUMBER_ASSIGNED,
                null,
                Map.of(
                        "commercialNumber", assignedNumber,
                        "commercialNumberFormatted", formatCommercialNumber(assignedNumber)
                )
        );

        return toDetailResponse(ticket, lines);
    }

    /**
     * Asigna el próximo número comercial del negocio bajo bloqueo pesimista
     * (Fase 5.1). Si la fila de secuencia todavía no existe para el negocio
     * (por ejemplo, negocios creados antes de esta fase que no pasaron por
     * la migración) se crea de forma perezosa, tolerando la carrera con
     * otra transacción que intente lo mismo.
     */
    private Long assignCommercialNumber(Ticket ticket, Long tenantId) {
        TicketNumberSequence sequence = ticketNumberSequenceRepository.findByNegocioIdForUpdate(tenantId)
                .orElseGet(() -> createSequence(tenantId));

        Long assignedNumber = sequence.getNextNumber();
        sequence.setNextNumber(assignedNumber + 1);
        ticketNumberSequenceRepository.save(sequence);

        ticket.setCommercialNumber(assignedNumber);
        return assignedNumber;
    }

    private TicketNumberSequence createSequence(Long tenantId) {
        try {
            return ticketNumberSequenceRepository.saveAndFlush(
                    TicketNumberSequence.builder()
                            .negocioId(tenantId)
                            .nextNumber(1L)
                            .version(0L)
                            .build()
            );
        } catch (DataIntegrityViolationException race) {
            return ticketNumberSequenceRepository.findByNegocioIdForUpdate(tenantId)
                    .orElseThrow(() -> new IllegalStateException(
                            "No se pudo inicializar la secuencia de numeración del negocio " + tenantId
                    ));
        }
    }

    // ------------------------------------------------------------------
    // Cancelaciones
    // ------------------------------------------------------------------

    @Transactional
    public TicketDetailResponseDto cancelLine(Long ticketId, Long lineId) {
        Long tenantId = currentUser.getTenantId();

        Ticket ticket = ticketRepository.findByIdAndNegocioIdForUpdate(ticketId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket no encontrado"));

        TicketLine line;

        try {
            ensureOpen(ticket);

            line = ticketLineRepository
                    .findByIdAndTicketIdAndTicketNegocioId(lineId, ticketId, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Línea no encontrada"));

            if (line.getStatus() == TicketLineStatus.CANCELLED) {
                throw new BusinessRuleException("LINE_ALREADY_CANCELLED", "La línea ya está cancelada");
            }
        } catch (BusinessRuleException | ConflictException ex) {
            auditFailureSafely(AuditEntityType.TICKET_LINE, lineId, AuditAction.TICKET_LINE_CANCELLED, ex.getCode());
            throw ex;
        }

        line.setStatus(TicketLineStatus.CANCELLED);
        ticketLineRepository.save(line);

        // El recálculo nunca falla por falta de líneas activas: si esta era
        // la última, el total simplemente queda en cero (Fase 2.2).
        recalculateTicketTotal(ticket);
        ticketRepository.save(ticket);

        List<TicketLineResponseDto> lines = ticketLineRepository
                .findByTicketIdOrderByCreatedAtAsc(ticket.getId())
                .stream()
                .map(this::toLineResponse)
                .toList();

        auditEventService.recordSuccess(
                AuditEntityType.TICKET_LINE,
                line.getId(),
                AuditAction.TICKET_LINE_CANCELLED,
                Map.of("status", TicketLineStatus.ACTIVE, "subtotal", line.getSubtotal()),
                Map.of("status", TicketLineStatus.CANCELLED),
                Map.of("ticketId", ticketId)
        );

        return toDetailResponse(ticket, lines);
    }

    @Transactional
    public TicketDetailResponseDto cancelBatch(Long ticketId, Integer batchNumber) {
        Long tenantId = currentUser.getTenantId();

        Ticket ticket = ticketRepository.findByIdAndNegocioIdForUpdate(ticketId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket no encontrado"));

        List<TicketLine> activeLines;

        try {
            ensureOpen(ticket);

            List<TicketLine> lines = ticketLineRepository
                .findByTicketIdAndBatchNumberAndTicketNegocioId(ticketId, batchNumber, tenantId);

            if (lines.isEmpty()) {
                throw new ResourceNotFoundException("Batch no encontrado");
            }

            activeLines = lines.stream()
                    .filter(line -> line.getStatus() == TicketLineStatus.ACTIVE)
                    .toList();

            if (activeLines.isEmpty()) {
                throw new BusinessRuleException("BATCH_ALREADY_CANCELLED", "El batch ya no tiene líneas activas");
            }
        } catch (BusinessRuleException | ConflictException ex) {
            auditFailureSafely(AuditEntityType.TICKET, ticketId, AuditAction.TICKET_BATCH_CANCELLED, ex.getCode());
            throw ex;
        }

        activeLines.forEach(line -> line.setStatus(TicketLineStatus.CANCELLED));
        ticketLineRepository.saveAll(activeLines);

        recalculateTicketTotal(ticket);
        ticketRepository.save(ticket);

        List<TicketLineResponseDto> allLines = ticketLineRepository
                .findByTicketIdOrderByCreatedAtAsc(ticket.getId())
                .stream()
                .map(this::toLineResponse)
                .toList();

        auditEventService.recordSuccess(
                AuditEntityType.TICKET,
                ticket.getId(),
                AuditAction.TICKET_BATCH_CANCELLED,
                null,
                Map.of("total", ticket.getTotal()),
                Map.of("batchNumber", batchNumber, "cancelledLines", activeLines.size())
        );

        return toDetailResponse(ticket, allLines);
    }

    @Transactional
    public TicketDetailResponseDto cancelTicket(Long ticketId) {
        Long tenantId = currentUser.getTenantId();
        Long userId = currentUser.getUserId();

        Ticket ticket = ticketRepository.findByIdAndNegocioIdForUpdate(ticketId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket no encontrado"));

        try {
            ensureOpen(ticket);
        } catch (BusinessRuleException | ConflictException ex) {
            auditFailureSafely(AuditEntityType.TICKET, ticketId, AuditAction.TICKET_CANCELLED, ex.getCode());
            throw ex;
        }

        List<TicketLine> lines = ticketLineRepository
            .findByTicketIdAndTicketNegocioIdOrderByCreatedAtAsc(ticket.getId(), tenantId);

        List<TicketLine> activeLines = lines.stream()
                .filter(line -> line.getStatus() == TicketLineStatus.ACTIVE)
                .toList();

        activeLines.forEach(line -> line.setStatus(TicketLineStatus.CANCELLED));
        ticketLineRepository.saveAll(activeLines);

        recalculateTicketTotal(ticket); // queda en 0

        User cancelledBy = userRepository.findByIdAndNegocioId(userId, tenantId).orElse(null);

        ticket.setStatus(TicketStatus.CANCELLED);
        ticket.setCancelledAt(LocalDateTime.now());
        ticket.setCancelledBy(cancelledBy);
        ticketRepository.save(ticket);

        freeMesa(ticket, tenantId);

        List<TicketLineResponseDto> responseLines = lines.stream()
                .map(this::toLineResponse)
                .toList();

        auditEventService.recordSuccess(
                AuditEntityType.TICKET,
                ticket.getId(),
                AuditAction.TICKET_CANCELLED,
                null,
                ticketSnapshot(ticket)
        );

        return toDetailResponse(ticket, responseLines);
    }

    private void freeMesa(Ticket ticket, Long tenantId) {
        if (ticket.getMesa() == null) {
            return;
        }

        Mesa mesa = mesaRepository.findByIdAndNegocioIdForUpdate(ticket.getMesa().getId(), tenantId)
                .orElse(ticket.getMesa());
        mesa.setStatus(MesaStatus.FREE);
        mesaRepository.save(mesa);
    }

    // ------------------------------------------------------------------
    // Cambio de mesa
    // ------------------------------------------------------------------

    @Transactional
    public TicketDetailResponseDto changeMesa(Long ticketId, ChangeTicketMesaRequestDto dto) {
        Long tenantId = currentUser.getTenantId();

        Ticket ticket = ticketRepository.findByIdAndNegocioIdForUpdate(ticketId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket no encontrado"));

        try {
            ensureOpen(ticket);
        } catch (BusinessRuleException | ConflictException ex) {
            auditFailureSafely(AuditEntityType.TICKET, ticketId, AuditAction.TICKET_TABLE_CHANGED, ex.getCode());
            throw ex;
        }

        Long originMesaId = ticket.getMesa().getId();
        Long destinationMesaId = dto.getMesaId();

        if (originMesaId.equals(destinationMesaId)) {
            List<TicketLineResponseDto> sameLines = currentLines(ticket, tenantId);
            return toDetailResponse(ticket, sameLines);
        }

        // Orden determinista de bloqueo por id ascendente (Fase 3.9) para
        // evitar deadlocks entre dos cambios de mesa cruzados.
        Long firstId = Math.min(originMesaId, destinationMesaId);
        Long secondId = Math.max(originMesaId, destinationMesaId);

        Mesa firstMesa = mesaRepository.findByIdAndNegocioIdForUpdate(firstId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa no encontrada"));
        Mesa secondMesa = mesaRepository.findByIdAndNegocioIdForUpdate(secondId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa no encontrada"));

        Mesa origen = firstId.equals(originMesaId) ? firstMesa : secondMesa;
        Mesa destino = firstId.equals(destinationMesaId) ? firstMesa : secondMesa;

        try {
            if (!Boolean.TRUE.equals(destino.getActiva())) {
                throw new BusinessRuleException("MESA_INACTIVE", "No se puede mover el ticket a una mesa inactiva");
            }

            Optional<Ticket> existingOpenTicket = ticketRepository
                    .findByMesaIdAndNegocioIdAndStatus(destino.getId(), tenantId, TicketStatus.OPEN);

            if (existingOpenTicket.isPresent() && !existingOpenTicket.get().getId().equals(ticket.getId())) {
                throw new ConflictException("TABLE_ALREADY_OCCUPIED", "La mesa seleccionada ya tiene un ticket abierto");
            }
        } catch (BusinessRuleException | ConflictException ex) {
            auditFailureSafely(AuditEntityType.TICKET, ticketId, AuditAction.TICKET_TABLE_CHANGED, ex.getCode());
            throw ex;
        }

        ticket.setMesa(destino);
        ticketRepository.save(ticket);

        origen.setStatus(MesaStatus.FREE);
        mesaRepository.save(origen);

        destino.setStatus(MesaStatus.OCCUPIED);
        mesaRepository.save(destino);

        auditEventService.recordSuccess(
                AuditEntityType.TICKET,
                ticket.getId(),
                AuditAction.TICKET_TABLE_CHANGED,
                Map.of("mesaId", originMesaId),
                Map.of("mesaId", destinationMesaId)
        );

        return toDetailResponse(ticket, currentLines(ticket, tenantId));
    }

    // ------------------------------------------------------------------
    // Descuentos por línea (Fase 5.1)
    // ------------------------------------------------------------------

    @Transactional
    public TicketDetailResponseDto applyLineDiscount(Long ticketId, Long lineId, ApplyLineDiscountRequestDto dto) {
        Long tenantId = currentUser.getTenantId();
        Long userId = currentUser.getUserId();

        Ticket ticket = ticketRepository.findByIdAndNegocioIdForUpdate(ticketId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket no encontrado"));

        TicketLine line;
        Integer previousPercentage;
        BigDecimal previousSubtotal;
        BigDecimal previousSubtotalBeforeDiscount;
        BigDecimal previousDiscountAmount;

        try {
            ensureOpen(ticket);

            line = ticketLineRepository.findByIdAndTicketIdAndTicketNegocioId(lineId, ticketId, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Línea no encontrada"));

            if (line.getStatus() != TicketLineStatus.ACTIVE) {
                throw new BusinessRuleException("LINE_NOT_ACTIVE", "Solo se puede aplicar descuento a una línea activa");
            }

            previousPercentage = line.getDiscountPercentage();
            previousSubtotal = line.getSubtotal();
            previousSubtotalBeforeDiscount = line.getSubtotalBeforeDiscount();
            previousDiscountAmount = line.getDiscountAmount();

            TicketLinePricingService.LineDiscountCalculation calculation = ticketLinePricingService.calculate(
                    line.getUnitPriceSnapshot(),
                    line.getQuantity(),
                    dto.getDiscountPercentage()
            );

            line.setSubtotalBeforeDiscount(calculation.subtotalBeforeDiscount());
            line.setDiscountPercentage(dto.getDiscountPercentage());
            line.setDiscountAmount(calculation.discountAmount());
            line.setSubtotal(calculation.subtotal());

            if (ticketLinePricingService.isNoDiscount(dto.getDiscountPercentage())) {
                line.setDiscountAppliedBy(null);
                line.setDiscountAppliedAt(null);
            } else {
                User actor = userRepository.findByIdAndNegocioId(userId, tenantId)
                        .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
                line.setDiscountAppliedBy(actor);
                line.setDiscountAppliedAt(LocalDateTime.now());
            }
        } catch (BusinessRuleException | ConflictException ex) {
            auditFailureSafely(AuditEntityType.TICKET_LINE, lineId, AuditAction.LINE_DISCOUNT_APPLIED, ex.getCode());
            throw ex;
        }

        ticketLineRepository.save(line);

        // Recálculo del total del ticket a partir de las líneas ACTIVE
        // (Fase 5.1): el descuento de una línea nunca invalida el ticket.
        recalculateTicketTotal(ticket);
        ticketRepository.save(ticket);

        boolean wasDiscounted = previousPercentage != null && previousPercentage != 0;
        boolean isNowDiscounted = !ticketLinePricingService.isNoDiscount(dto.getDiscountPercentage());
        AuditAction action = (!isNowDiscounted && wasDiscounted)
                ? AuditAction.LINE_DISCOUNT_REMOVED
                : AuditAction.LINE_DISCOUNT_APPLIED;

        auditEventService.recordSuccess(
                AuditEntityType.TICKET_LINE,
                line.getId(),
                action,
                Map.of(
                        "discountPercentage", previousPercentage,
                        "subtotalBeforeDiscount", previousSubtotalBeforeDiscount,
                        "discountAmount", previousDiscountAmount,
                        "subtotal", previousSubtotal
                ),
                Map.of(
                        "discountPercentage", line.getDiscountPercentage(),
                        "subtotalBeforeDiscount", line.getSubtotalBeforeDiscount(),
                        "discountAmount", line.getDiscountAmount(),
                        "subtotal", line.getSubtotal()
                ),
                Map.of("ticketId", ticketId)
        );

        return toDetailResponse(ticket, currentLines(ticket, tenantId));
    }

    private List<TicketLineResponseDto> currentLines(Ticket ticket, Long tenantId) {
        return ticketLineRepository
                .findByTicketIdAndTicketNegocioIdOrderByCreatedAtAsc(ticket.getId(), tenantId)
                .stream()
                .map(this::toLineResponse)
                .toList();
    }

    // ------------------------------------------------------------------
    // Consultas / reportes (sin bloqueo, no mutan estado)
    // ------------------------------------------------------------------

    public PaymentMethodSummaryDto getPaymentMethodSummary(LocalDate from, LocalDate to) {
        Long tenantId = currentUser.getTenantId();
        validateDateRange(from, to);

        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.atTime(23, 59, 59);

        BigDecimal cashTotal = ticketRepository
                .sumTotalByNegocioIdAndStatusAndPaymentMethodAndPaidAtBetween(
                        tenantId,
                        TicketStatus.PAID,
                        PaymentMethod.CASH,
                        fromDateTime,
                        toDateTime
                );

        BigDecimal cardTotal = ticketRepository
                .sumTotalByNegocioIdAndStatusAndPaymentMethodAndPaidAtBetween(
                        tenantId,
                        TicketStatus.PAID,
                        PaymentMethod.CARD,
                        fromDateTime,
                        toDateTime
                );

        BigDecimal totalSales = ticketRepository
            .sumTotalByNegocioIdAndStatusAndPaidAtBetween(
                    tenantId,
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

    @Transactional(readOnly = true)
    public TicketDetailResponseDto findDetailById(Long ticketId) {
        Long tenantId = currentUser.getTenantId();

        Ticket ticket = ticketRepository.findByIdAndNegocioId(ticketId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket no encontrado"));

        return toDetailResponse(ticket, currentLines(ticket, tenantId));
    }

    public List<TicketResponseDto> findPaidTicketsByDateRange(LocalDate from, LocalDate to) {
        Long tenantId = currentUser.getTenantId();

        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.atTime(23, 59, 59);

        return ticketRepository
                .findByNegocioIdAndStatusAndPaidAtBetweenOrderByPaidAtDesc(
                        tenantId,
                        TicketStatus.PAID,
                        fromDateTime,
                        toDateTime
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<UserSalesSummaryDto> getUserSalesSummary(LocalDate from, LocalDate to) {
        Long tenantId = currentUser.getTenantId();
        validateDateRange(from, to);

        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.atTime(23, 59, 59);

        return ticketRepository.findUserSalesSummaryByNegocioIdAndStatusAndPaidAtBetween(
                tenantId,
                TicketStatus.PAID,
                fromDateTime,
                toDateTime
        );
    }

    public List<TicketResponseDto> findPaidTicketsFiltered(LocalDate from, LocalDate to) {
        if ((from == null && to != null) || (from != null && to == null)) {
            throw new BusinessRuleException("INVALID_DATE_RANGE", "Debes enviar ambas fechas: from y to");
        }

        if (from != null && from.isAfter(to)) {
            throw new BusinessRuleException("INVALID_DATE_RANGE", "La fecha 'from' no puede ser mayor que 'to'");
        }

        if (from != null) {
            return findPaidTicketsByDateRange(from, to);
        }

        return findPaidTickets();
    }

    public List<TicketResponseDto> findOpenTickets() {
        Long tenantId = currentUser.getTenantId();

        return ticketRepository.findByNegocioIdAndStatusOrderByCreatedAtDesc(tenantId, TicketStatus.OPEN)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<TicketResponseDto> findPaidTickets() {
        Long tenantId = currentUser.getTenantId();

        return ticketRepository.findByNegocioIdAndStatusOrderByPaidAtDesc(tenantId, TicketStatus.PAID)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    public List<TicketResponseDto> findCancelledTickets() {
        Long tenantId = currentUser.getTenantId();

        return ticketRepository
                .findByNegocioIdAndStatusOrderByUpdatedAtDesc(tenantId, TicketStatus.CANCELLED)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public BigDecimal getTotalSales(LocalDate from, LocalDate to) {
        Long tenantId = currentUser.getTenantId();
        validateDateRange(from, to);

        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.atTime(23, 59, 59);

        BigDecimal total = ticketRepository
                .sumTotalByNegocioIdAndStatusAndPaidAtBetween(
                        tenantId,
                        TicketStatus.PAID,
                        fromDateTime,
                        toDateTime
                );

        return total != null ? total : BigDecimal.ZERO;
    }

    public CashClosingSummaryDto getCashClosingSummary(LocalDate from, LocalDate to) {
        Long tenantId = currentUser.getTenantId();
        validateDateRange(from, to);

        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.atTime(23, 59, 59);

        BigDecimal totalSales = ticketRepository.sumTotalByNegocioIdAndStatusAndPaidAtBetween(
                tenantId,
                TicketStatus.PAID,
                fromDateTime,
                toDateTime
        );

        BigDecimal cashTotal = ticketRepository.sumTotalByNegocioIdAndStatusAndPaymentMethodAndPaidAtBetween(
                tenantId,
                TicketStatus.PAID,
                PaymentMethod.CASH,
                fromDateTime,
                toDateTime
        );

        BigDecimal cardTotal = ticketRepository.sumTotalByNegocioIdAndStatusAndPaymentMethodAndPaidAtBetween(
                tenantId,
                TicketStatus.PAID,
                PaymentMethod.CARD,
                fromDateTime,
                toDateTime
        );

        Long paidTickets = ticketRepository.countByNegocioIdAndStatusAndPaidAtBetween(
                tenantId,
                TicketStatus.PAID,
                fromDateTime,
                toDateTime
        );

        Long cancelledTickets = ticketRepository.countByNegocioIdAndStatusAndUpdatedAtBetween(
                tenantId,
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

    public PageResponseDto<TicketResponseDto> findOpenTicketsPaged(int page, int size) {
        Long tenantId = currentUser.getTenantId();

        Pageable pageable = PageRequest.of(page, size);

        Page<Ticket> ticketPage = ticketRepository
            .findByNegocioIdAndStatusOrderByCreatedAtDesc(
                tenantId,
                TicketStatus.OPEN,
                pageable
            );

        return toPageResponse(ticketPage);
    }

    public PageResponseDto<TicketResponseDto> findCancelledTicketsPaged(int page, int size) {
        Long tenantId = currentUser.getTenantId();

        Pageable pageable = PageRequest.of(page, size);

        Page<Ticket> ticketPage = ticketRepository
            .findByNegocioIdAndStatusOrderByUpdatedAtDesc(
                tenantId,
                TicketStatus.CANCELLED,
                pageable
            );

        return toPageResponse(ticketPage);
    }

    public List<ProductSalesSummaryDto> getProductSalesSummary(LocalDate from, LocalDate to) {
        Long tenantId = currentUser.getTenantId();

        validateDateRange(from, to);

        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.atTime(23, 59, 59);

        return ticketLineRepository.findProductSalesSummary(
                tenantId,
                TicketStatus.PAID,
                TicketLineStatus.ACTIVE,
                fromDateTime,
                toDateTime
        );
    }

    public List<DailySalesSummaryDto> getDailySalesSummary(LocalDate from, LocalDate to) {
        Long tenantId = currentUser.getTenantId();

        validateDateRange(from, to);

        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.atTime(23, 59, 59);

        List<Ticket> paidTickets = ticketRepository
                .findByNegocioIdAndStatusAndPaidAtBetweenOrderByPaidAtDesc(
                        tenantId,
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
        Long tenantId = currentUser.getTenantId();

        validateDateRange(from, to);

        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.atTime(23, 59, 59);

        BigDecimal totalSales = ticketRepository.sumTotalByNegocioIdAndStatusAndPaidAtBetween(
                tenantId,
                TicketStatus.PAID,
                fromDateTime,
                toDateTime
        );

        Long ticketCount = ticketRepository.countByNegocioIdAndStatusAndPaidAtBetween(
                tenantId,
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
        Long tenantId = currentUser.getTenantId();

        Ticket ticket = ticketRepository.findByIdAndNegocioIdForUpdate(ticketId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket no encontrado"));

        ensureOpen(ticket);

        ticket.setNotes(normalizeNotes(dto.getNotes()));
        ticketRepository.save(ticket);

        return toDetailResponse(ticket, currentLines(ticket, tenantId));
    }

    public PageResponseDto<TicketResponseDto> findPaidTicketsPaged(
        LocalDate from,
        LocalDate to,
        int page,
        int size
    ) {
        validateDateRange(from, to);

        Long tenantId = currentUser.getTenantId();

        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.atTime(23, 59, 59);

        Pageable pageable = PageRequest.of(page, size);

        Page<Ticket> ticketPage = ticketRepository
            .findByNegocioIdAndStatusAndPaidAtBetweenOrderByPaidAtDesc(
                tenantId,
                TicketStatus.PAID,
                fromDateTime,
                toDateTime,
                pageable
            );

        return toPageResponse(ticketPage);
    }

    // ------------------------------------------------------------------
    // Helpers privados
    // ------------------------------------------------------------------

    private PageResponseDto<TicketResponseDto> toPageResponse(Page<Ticket> ticketPage) {
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

    private String buildGroupKey(Long productId, String notes) {
        return productId + "::" + (notes == null ? "" : notes.trim().toLowerCase());
    }

    private TicketResponseDto toResponse(Ticket ticket) {
        return TicketResponseDto.builder()
                .id(ticket.getId())
                .status(ticket.getStatus())
                .total(ticket.getTotal())
                .commercialNumber(ticket.getCommercialNumber())
                .commercialNumberFormatted(formatCommercialNumber(ticket.getCommercialNumber()))
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

    /**
     * Validación central de estado (Fase 2.1): solo un ticket OPEN puede
     * modificarse. Se centraliza aquí para no repetir reglas inconsistentes
     * en cada operación.
     */
    private void ensureOpen(Ticket ticket) {
        switch (ticket.getStatus()) {
            case OPEN -> {
                // ok
            }
            case PAID -> throw new BusinessRuleException("TICKET_ALREADY_PAID", "El ticket ya está pagado y no puede modificarse");
            case CANCELLED -> throw new BusinessRuleException("TICKET_ALREADY_CANCELLED", "El ticket está cancelado y no puede modificarse");
        }
    }

    /**
     * Recalcula el total únicamente a partir de líneas activas. Nunca
     * lanza excepción por falta de líneas: en ese caso el total es cero
     * (Fase 2.2/2.10). La validación de "ticket vacío" vive solo en pay().
     */
    private void recalculateTicketTotal(Ticket ticket) {
        List<TicketLine> activeLines = ticketLineRepository.findByTicketIdAndStatusOrderByCreatedAtAsc(
                ticket.getId(),
                TicketLineStatus.ACTIVE
        );

        ticket.setTotal(sumActive(activeLines));
    }

    private BigDecimal sumActive(List<TicketLine> activeLines) {
        return activeLines.stream()
                .map(TicketLine::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
                .commercialNumber(ticket.getCommercialNumber())
                .commercialNumberFormatted(formatCommercialNumber(ticket.getCommercialNumber()))
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
        User discountAppliedBy = line.getDiscountAppliedBy();

        return TicketLineResponseDto.builder()
                .id(line.getId())
                .productId(line.getProductId())
                .productNameSnapshot(line.getProductNameSnapshot())
                .unitPriceSnapshot(line.getUnitPriceSnapshot())
                .quantity(line.getQuantity())
                .subtotal(line.getSubtotal())
                .subtotalBeforeDiscount(line.getSubtotalBeforeDiscount())
                .discountPercentage(line.getDiscountPercentage())
                .discountAmount(line.getDiscountAmount())
                .discountAppliedById(discountAppliedBy != null ? discountAppliedBy.getId() : null)
                .discountAppliedByUsername(discountAppliedBy != null ? discountAppliedBy.getUsername() : null)
                .discountAppliedAt(line.getDiscountAppliedAt())
                .batchNumber(line.getBatchNumber())
                .status(line.getStatus())
                .notes(line.getNotes())
                .createdAt(line.getCreatedAt())
                .build();
    }

    /**
     * Formato de exhibición del número comercial (Fase 5.1): 6 dígitos con
     * ceros a la izquierda. Nulo mientras el ticket no está pagado.
     */
    private String formatCommercialNumber(Long commercialNumber) {
        if (commercialNumber == null) {
            return null;
        }

        return String.format("%06d", commercialNumber);
    }

    /**
     * Snapshot seguro del ticket para auditoría (Fase 5.3): solo campos
     * simples, nunca la entidad completa ni sus relaciones perezosas
     * completas.
     */
    private Map<String, Object> ticketSnapshot(Ticket ticket) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("status", ticket.getStatus());
        snapshot.put("total", ticket.getTotal());
        snapshot.put("commercialNumber", ticket.getCommercialNumber());
        snapshot.put("mesaId", ticket.getMesa() != null ? ticket.getMesa().getId() : null);
        snapshot.put("paymentMethod", ticket.getPaymentMethod());
        return snapshot;
    }

    /**
     * Registra un fallo funcional en una transacción independiente
     * (Fase 5.3) sin permitir que un problema en la propia auditoría
     * oculte la excepción de negocio original.
     */
    private void auditFailureSafely(AuditEntityType entityType, Long entityId, AuditAction action, String errorCode) {
        try {
            auditFailureRecorder.recordFailure(entityType, entityId, action, errorCode, null);
        } catch (RuntimeException ignored) {
            // La auditoría de fallos nunca debe ocultar el error funcional original.
        }
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessRuleException("INVALID_DATE_RANGE", "Debes enviar ambas fechas");
        }

        if (from.isAfter(to)) {
            throw new BusinessRuleException("INVALID_DATE_RANGE", "La fecha 'from' no puede ser mayor que 'to'");
        }
    }

    private static class DailyAccumulator {
        private long ticketCount = 0L;
        private BigDecimal totalSales = BigDecimal.ZERO;
    }
}
