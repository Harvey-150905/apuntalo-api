package com.harbeyescala.api_apuntalo.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.harbeyescala.api_apuntalo.dto.AddTicketLinesRequestDto;
import com.harbeyescala.api_apuntalo.dto.ApplyLineDiscountRequestDto;
import com.harbeyescala.api_apuntalo.dto.CashClosingSummaryDto;
import com.harbeyescala.api_apuntalo.dto.CashSessionReferenceDto;
import com.harbeyescala.api_apuntalo.dto.ChangeTicketMesaRequestDto;
import com.harbeyescala.api_apuntalo.dto.PageResponseDto;
import com.harbeyescala.api_apuntalo.dto.PayTicketRequestDto;
import com.harbeyescala.api_apuntalo.dto.PaymentMethodSummaryDto;
import com.harbeyescala.api_apuntalo.dto.PaymentComponentRequestDto;
import com.harbeyescala.api_apuntalo.dto.PaymentResponseDto;
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
import com.harbeyescala.api_apuntalo.entity.CashSession;
import com.harbeyescala.api_apuntalo.entity.Payment;
import com.harbeyescala.api_apuntalo.entity.Negocio;
import com.harbeyescala.api_apuntalo.entity.Product;
import com.harbeyescala.api_apuntalo.entity.Ticket;
import com.harbeyescala.api_apuntalo.entity.TicketLine;
import com.harbeyescala.api_apuntalo.entity.TicketNumberSequence;
import com.harbeyescala.api_apuntalo.entity.User;
import com.harbeyescala.api_apuntalo.entity.enums.AuditAction;
import com.harbeyescala.api_apuntalo.entity.enums.CashSessionStatus;
import com.harbeyescala.api_apuntalo.entity.enums.AuditEntityType;
import com.harbeyescala.api_apuntalo.entity.enums.MesaStatus;
import com.harbeyescala.api_apuntalo.entity.enums.PaymentMethod;
import com.harbeyescala.api_apuntalo.entity.enums.TicketLineStatus;
import com.harbeyescala.api_apuntalo.entity.enums.TicketStatus;
import com.harbeyescala.api_apuntalo.exception.BusinessRuleException;
import com.harbeyescala.api_apuntalo.exception.ConflictException;
import com.harbeyescala.api_apuntalo.exception.CashSessionNotFoundException;
import com.harbeyescala.api_apuntalo.exception.ResourceNotFoundException;
import com.harbeyescala.api_apuntalo.repository.MesaRepository;
import com.harbeyescala.api_apuntalo.repository.CashSessionRepository;
import com.harbeyescala.api_apuntalo.repository.PaymentRepository;
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
    private final PaymentRepository paymentRepository;
    private final CashSessionRepository cashSessionRepository;
    private final TicketLinePricingService ticketLinePricingService;
    private final AuditEventService auditEventService;
    private final AuditFailureRecorder auditFailureRecorder;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final ActiveStoreContext storeContext;

    public TicketService(
            TicketRepository ticketRepository,
            MesaRepository mesaRepository,
            NegocioRepository negocioRepository,
            UserRepository userRepository,
            TicketLineRepository ticketLineRepository,
            ProductRepository productRepository,
            TicketNumberSequenceRepository ticketNumberSequenceRepository,
            PaymentRepository paymentRepository,
            CashSessionRepository cashSessionRepository,
            TicketLinePricingService ticketLinePricingService,
            AuditEventService auditEventService,
            AuditFailureRecorder auditFailureRecorder,
            CurrentUser currentUser,
            Clock clock,
            ActiveStoreContext storeContext
    ) {
        this.ticketRepository = ticketRepository;
        this.mesaRepository = mesaRepository;
        this.negocioRepository = negocioRepository;
        this.userRepository = userRepository;
        this.ticketLineRepository = ticketLineRepository;
        this.productRepository = productRepository;
        this.ticketNumberSequenceRepository = ticketNumberSequenceRepository;
        this.paymentRepository = paymentRepository;
        this.cashSessionRepository = cashSessionRepository;
        this.ticketLinePricingService = ticketLinePricingService;
        this.auditEventService = auditEventService;
        this.auditFailureRecorder = auditFailureRecorder;
        this.currentUser = currentUser;
        this.clock = clock;
        this.storeContext=storeContext;
    }

    // ------------------------------------------------------------------
    // Creación
    // ------------------------------------------------------------------

    @Transactional
    public TicketResponseDto create(TicketRequestDto dto) {
        Long tenantId = currentUser.getTenantId();
        Long userId = currentUser.getUserId();
        Long storeId = storeContext.storeId();

        CashSession originSession = lockUsableSession(dto.getOriginCashSessionId(), tenantId);

        // Orden global: sesión -> mesa.
        Mesa mesa = mesaRepository.findByIdAndNegocioIdAndStoreIdForUpdate(dto.getMesaId(), tenantId,storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa no encontrada"));

        try {
            if (!Boolean.TRUE.equals(mesa.getActiva())) {
                throw new BusinessRuleException("MESA_INACTIVE", "La mesa está desactivada");
            }

            boolean alreadyOpen = ticketRepository.existsByMesaIdAndNegocioIdAndStoreIdAndStatus(
                    mesa.getId(),
                    tenantId,
                    storeId,
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
                .store(mesa.getStore())
                .createdBy(createdBy)
                .originCashSession(originSession)
                .originSessionLegacy(false)
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

    @Transactional(readOnly = true)
    public TicketResponseDto findOpenByMesa(Long mesaId) {
        Long tenantId = currentUser.getTenantId();

        mesaRepository.findByIdAndNegocioIdAndStoreId(mesaId, tenantId,storeContext.storeId())
                .orElseThrow(() -> new ResourceNotFoundException("Mesa no encontrada"));

        Ticket ticket = ticketRepository.findByMesaIdAndNegocioIdAndStoreIdAndStatus(
                mesaId,
                tenantId,
                storeContext.storeId(),
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
        Ticket ticket = ticketRepository.findByIdAndNegocioIdAndStoreIdForUpdate(ticketId, tenantId,storeContext.storeId())
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
                Product product = productRepository.findByIdAndNegocioIdAndStoreId(lineDto.getProductId(), tenantId,storeContext.storeId())
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
                        .store(ticket.getStore())
                        .negocio(ticket.getNegocio())
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

        CashSession paymentSession = lockUsableSession(dto.getCashSessionId(), tenantId);

        // Orden global: sesión de cobro -> ticket.
        // Bloqueo de escritura antes de comprobar el estado (Fase 3.6): la
        // segunda petición concurrente espera a que la primera termine y
        // encuentra el ticket ya PAID, sin volver a cobrar.
        Ticket ticket = ticketRepository.findByIdAndNegocioIdAndStoreIdForUpdate(ticketId, tenantId,storeContext.storeId())
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

        List<ValidatedPayment> validatedPayments = validatePayments(dto, ticket.getTotal());

        User paidBy = userRepository.findByIdAndNegocioId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        LocalDateTime paidAt = LocalDateTime.now(clock);
        List<Payment> payments = validatedPayments.stream()
                .map(component -> Payment.builder()
                        .negocioId(tenantId)
                        .store(ticket.getStore())
                        .ticket(ticket)
                        .cashSession(paymentSession)
                        .method(component.method())
                        .amount(component.amount())
                        .cashReceived(component.cashReceived())
                        .changeGiven(component.changeGiven())
                        .paidBy(paidBy)
                        .paidAt(paidAt)
                        .legacyImported(false)
                        .sessionLegacy(false)
                        .createdAt(paidAt)
                        .build())
                .toList();
        paymentRepository.saveAllAndFlush(payments);

        ticket.setStatus(TicketStatus.PAID);
        ticket.setPaidAt(paidAt);
        ticket.setPaidBy(paidBy);
        ticket.setPaymentMethod(validatedPayments.size() == 2
                ? PaymentMethod.MIXED
                : validatedPayments.get(0).method());

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
                ticketSnapshot(ticket),
                paymentAuditMetadata(ticket, payments, paidBy)
        );
        auditEventService.recordSuccess(
                AuditEntityType.TICKET,
                ticket.getId(),
                AuditAction.COMMERCIAL_NUMBER_ASSIGNED,
                null,
                Map.of(
                        "commercialNumber", assignedNumber,
                        "commercialNumberFormatted", formatCommercialNumber(assignedNumber),
                        "storeId", ticket.getStore().getId()
                )
        );

        return toDetailResponse(ticket, lines, payments);
    }

    /**
     * Asigna el próximo número comercial de la Store bajo bloqueo pesimista
     * (Fase 5.1). Si la fila de secuencia todavía no existe para el negocio
     * (por ejemplo, negocios creados antes de esta fase que no pasaron por
     * la migración) se crea de forma perezosa, tolerando la carrera con
     * otra transacción que intente lo mismo.
     */
    private Long assignCommercialNumber(Ticket ticket, Long tenantId) {
        Long storeId=storeContext.storeId();
        if (!ticket.getStore().getId().equals(storeId)) throw new ResourceNotFoundException("Ticket no encontrado");
        Optional<TicketNumberSequence> existing= ticketNumberSequenceRepository.findByNegocioIdAndStoreIdForUpdate(tenantId,storeId);
        if(existing.isEmpty()) ticketNumberSequenceRepository.initializeIfAbsent(tenantId,storeId);
        TicketNumberSequence sequence=ticketNumberSequenceRepository.findByNegocioIdAndStoreIdForUpdate(tenantId,storeId)
                .orElseThrow(()->new IllegalStateException("No se pudo inicializar la secuencia comercial"));

        Long assignedNumber = sequence.getNextNumber();
        sequence.setNextNumber(assignedNumber + 1);
        ticketNumberSequenceRepository.save(sequence);

        ticket.setCommercialNumber(assignedNumber);
        return assignedNumber;
    }

    // ------------------------------------------------------------------
    // Cancelaciones
    // ------------------------------------------------------------------

    @Transactional
    public TicketDetailResponseDto cancelLine(Long ticketId, Long lineId) {
        Long tenantId = currentUser.getTenantId();

        Ticket ticket = ticketRepository.findByIdAndNegocioIdAndStoreIdForUpdate(ticketId, tenantId,storeContext.storeId())
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

        Ticket ticket = ticketRepository.findByIdAndNegocioIdAndStoreIdForUpdate(ticketId, tenantId,storeContext.storeId())
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

        Ticket ticket = ticketRepository.findByIdAndNegocioIdAndStoreIdForUpdate(ticketId, tenantId,storeContext.storeId())
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
        ticket.setCancelledAt(LocalDateTime.now(clock));
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

        Mesa mesa = mesaRepository.findByIdAndNegocioIdAndStoreIdForUpdate(ticket.getMesa().getId(), tenantId,storeContext.storeId())
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

        Ticket ticket = ticketRepository.findByIdAndNegocioIdAndStoreIdForUpdate(ticketId, tenantId,storeContext.storeId())
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

        Mesa firstMesa = mesaRepository.findByIdAndNegocioIdAndStoreIdForUpdate(firstId, tenantId,storeContext.storeId())
                .orElseThrow(() -> new ResourceNotFoundException("Mesa no encontrada"));
        Mesa secondMesa = mesaRepository.findByIdAndNegocioIdAndStoreIdForUpdate(secondId, tenantId,storeContext.storeId())
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

        Ticket ticket = ticketRepository.findByIdAndNegocioIdAndStoreIdForUpdate(ticketId, tenantId,storeContext.storeId())
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
                line.setDiscountAppliedAt(LocalDateTime.now(clock));
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
        LocalDateTime toDateTime = to.plusDays(1).atStartOfDay();

        BigDecimal cashTotal = paymentRepository.sumAmount(
                        tenantId,
                        storeContext.storeId(),
                        TicketStatus.PAID,
                        PaymentMethod.CASH,
                        fromDateTime,
                        toDateTime);

        BigDecimal cardTotal = paymentRepository.sumAmount(
                        tenantId,
                        storeContext.storeId(),
                        TicketStatus.PAID,
                        PaymentMethod.CARD,
                        fromDateTime,
                        toDateTime);

        BigDecimal totalSales = ticketRepository
            .sumTotalByNegocioIdAndStatusAndPaidAtBetween(
                    tenantId,
                    storeContext.storeId(),
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

        Ticket ticket = ticketRepository.findByIdAndNegocioIdAndStoreId(ticketId, tenantId,storeContext.storeId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket no encontrado"));

        return toDetailResponse(ticket, currentLines(ticket, tenantId));
    }

    @Transactional(readOnly = true)
    public List<TicketResponseDto> findPaidTicketsByDateRange(LocalDate from, LocalDate to) {
        Long tenantId = currentUser.getTenantId();

        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.plusDays(1).atStartOfDay();

        return toResponses(ticketRepository
                .findByNegocioIdAndStatusAndPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtDesc(
                        tenantId,
                        TicketStatus.PAID,
                        fromDateTime,
                        toDateTime
                ));
    }

    public List<UserSalesSummaryDto> getUserSalesSummary(LocalDate from, LocalDate to) {
        Long tenantId = currentUser.getTenantId();
        validateDateRange(from, to);

        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.plusDays(1).atStartOfDay();

        return ticketRepository.findUserSalesSummaryByNegocioIdAndStatusAndPaidAtBetween(
                tenantId,
                TicketStatus.PAID,
                fromDateTime,
                toDateTime
        );
    }

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public List<TicketResponseDto> findOpenTickets() {
        Long tenantId = currentUser.getTenantId();

        return toResponses(ticketRepository.findByNegocioIdAndStoreIdAndStatusOrderByCreatedAtDesc(
                tenantId,storeContext.storeId(), TicketStatus.OPEN));
    }

    @Transactional(readOnly = true)
    public List<TicketResponseDto> findPaidTickets() {
        Long tenantId = currentUser.getTenantId();

        return toResponses(ticketRepository.findByNegocioIdAndStoreIdAndStatusOrderByPaidAtDesc(
                tenantId,storeContext.storeId(), TicketStatus.PAID));
    }

    @Transactional(readOnly = true)
    public List<TicketResponseDto> findCancelledTickets() {
        Long tenantId = currentUser.getTenantId();

        return toResponses(ticketRepository
                .findByNegocioIdAndStoreIdAndStatusOrderByUpdatedAtDesc(tenantId,storeContext.storeId(), TicketStatus.CANCELLED));
    }

    public BigDecimal getTotalSales(LocalDate from, LocalDate to) {
        Long tenantId = currentUser.getTenantId();
        validateDateRange(from, to);

        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.plusDays(1).atStartOfDay();

        BigDecimal total = ticketRepository
                .sumTotalByNegocioIdAndStatusAndPaidAtBetween(
                        tenantId,
                        storeContext.storeId(),
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
        LocalDateTime toDateTime = to.plusDays(1).atStartOfDay();

        BigDecimal totalSales = ticketRepository.sumTotalByNegocioIdAndStatusAndPaidAtBetween(
                tenantId,
                storeContext.storeId(),
                TicketStatus.PAID,
                fromDateTime,
                toDateTime
        );

        BigDecimal cashTotal = paymentRepository.sumAmount(
                tenantId,
                storeContext.storeId(),
                TicketStatus.PAID,
                PaymentMethod.CASH,
                fromDateTime,
                toDateTime
        );

        BigDecimal cardTotal = paymentRepository.sumAmount(
                tenantId,
                storeContext.storeId(),
                TicketStatus.PAID,
                PaymentMethod.CARD,
                fromDateTime,
                toDateTime
        );

        Long paidTickets = ticketRepository.countByNegocioIdAndStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(
                tenantId,
                TicketStatus.PAID,
                fromDateTime,
                toDateTime
        );

        Long cancelledTickets = ticketRepository.countByNegocioIdAndStatusAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(
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

    @Transactional(readOnly = true)
    public PageResponseDto<TicketResponseDto> findOpenTicketsPaged(int page, int size) {
        PaginationPolicy.validate(page, size);
        Long tenantId = currentUser.getTenantId();

        Pageable pageable = PageRequest.of(page, size, org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("createdAt"),
                org.springframework.data.domain.Sort.Order.desc("id")));

        Page<Ticket> ticketPage = ticketRepository
            .findByNegocioIdAndStatusOrderByCreatedAtDesc(
                tenantId,
                TicketStatus.OPEN,
                pageable
            );

        return toPageResponse(ticketPage);
    }

    @Transactional(readOnly = true)
    public PageResponseDto<TicketResponseDto> findCancelledTicketsPaged(int page, int size) {
        PaginationPolicy.validate(page, size);
        Long tenantId = currentUser.getTenantId();

        Pageable pageable = PageRequest.of(page, size, org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("updatedAt"),
                org.springframework.data.domain.Sort.Order.desc("id")));

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
        LocalDateTime toDateTime = to.plusDays(1).atStartOfDay();

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
        LocalDateTime toDateTime = to.plusDays(1).atStartOfDay();

        List<Ticket> paidTickets = ticketRepository
                .findByNegocioIdAndStatusAndPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtDesc(
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
        LocalDateTime toDateTime = to.plusDays(1).atStartOfDay();

        BigDecimal totalSales = ticketRepository.sumTotalByNegocioIdAndStatusAndPaidAtBetween(
                tenantId,
                storeContext.storeId(),
                TicketStatus.PAID,
                fromDateTime,
                toDateTime
        );

        Long ticketCount = ticketRepository.countByNegocioIdAndStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(
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

        Ticket ticket = ticketRepository.findByIdAndNegocioIdAndStoreIdForUpdate(ticketId, tenantId,storeContext.storeId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket no encontrado"));

        ensureOpen(ticket);

        ticket.setNotes(normalizeNotes(dto.getNotes()));
        ticketRepository.save(ticket);

        return toDetailResponse(ticket, currentLines(ticket, tenantId));
    }

    @Transactional(readOnly = true)
    public PageResponseDto<TicketResponseDto> findPaidTicketsPaged(
        LocalDate from,
        LocalDate to,
        int page,
        int size
    ) {
        PaginationPolicy.validate(page, size);
        validateDateRange(from, to);

        Long tenantId = currentUser.getTenantId();

        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.plusDays(1).atStartOfDay();

        Pageable pageable = PageRequest.of(page, size, org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("paidAt"),
                org.springframework.data.domain.Sort.Order.desc("id")));

        Page<Ticket> ticketPage = ticketRepository
            .findByNegocioIdAndStatusAndPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtDesc(
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
        List<TicketResponseDto> content = toResponses(ticketPage.getContent());

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
        CashSession paymentSession = paymentRepository
                .findFirstByTicketIdAndNegocioIdOrderByIdAsc(ticket.getId(), ticket.getNegocio().getId())
                .map(Payment::getCashSession).orElse(null);
        return toResponse(ticket, paymentSession);
    }

    private List<TicketResponseDto> toResponses(List<Ticket> tickets) {
        if (tickets.isEmpty()) return List.of();
        Long tenantId = tickets.get(0).getNegocio().getId();
        List<Long> ids = tickets.stream().map(Ticket::getId).toList();
        Map<Long, CashSession> paymentSessions = new LinkedHashMap<>();
        paymentRepository.findByTicketIdInAndNegocioIdOrderByIdAsc(ids, tenantId)
                .forEach(p -> paymentSessions.putIfAbsent(p.getTicket().getId(), p.getCashSession()));
        return tickets.stream().map(t -> toResponse(t, paymentSessions.get(t.getId()))).toList();
    }

    private TicketResponseDto toResponse(Ticket ticket, CashSession paymentSession) {
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
                .originCashSession(toSessionReference(ticket.getOriginCashSession()))
                .paymentCashSession(toSessionReference(paymentSession))
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
        List<Payment> payments = ticket.getId() == null
                ? List.of()
                : paymentRepository.findByTicketIdAndNegocioIdOrderByIdAsc(ticket.getId(), ticket.getNegocio().getId());
        return toDetailResponse(ticket, lines, payments);
    }

    private CashSession lockUsableSession(Long sessionId, Long tenantId) {
        if (sessionId == null) {
            throw new BusinessRuleException("CASH_SESSION_REQUIRED", "La sesión de caja es obligatoria");
        }
        CashSession session = cashSessionRepository.findByIdAndNegocioIdAndStoreIdForUpdate(
                        sessionId, tenantId,storeContext.storeId())
                .orElseThrow(CashSessionNotFoundException::new);
        if (session.getStatus() != CashSessionStatus.OPEN) {
            throw new ConflictException("CASH_SESSION_CLOSED", "La sesión de caja está cerrada");
        }
        if (!Boolean.TRUE.equals(session.getCashRegister().getActive())) {
            throw new ConflictException("CASH_SESSION_REGISTER_INACTIVE", "La caja de la sesión está desactivada");
        }
        return session;
    }

    private TicketDetailResponseDto toDetailResponse(
            Ticket ticket, List<TicketLineResponseDto> lines, List<Payment> payments) {
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
                .payments(payments.stream().map(this::toPaymentResponse).toList())
                .paymentMethod(ticket.getPaymentMethod())
                .originCashSession(toSessionReference(ticket.getOriginCashSession()))
                .paymentCashSession(payments.stream().findFirst().map(Payment::getCashSession)
                        .map(this::toSessionReference).orElse(null))
                .build();
    }

    private CashSessionReferenceDto toSessionReference(CashSession session) {
        if (session == null) return null;
        return CashSessionReferenceDto.builder()
                .id(session.getId())
                .cashRegisterId(session.getCashRegister().getId())
                .cashRegisterName(session.getCashRegister().getName())
                .responsibleUserId(session.getOpenedBy().getId())
                .responsibleUsername(session.getOpenedBy().getUsername())
                .openedAt(session.getOpenedAt())
                .build();
    }

    private PaymentResponseDto toPaymentResponse(Payment payment) {
        return PaymentResponseDto.builder()
                .paymentId(payment.getId())
                .method(payment.getMethod())
                .amount(payment.getAmount())
                .cashReceived(payment.getCashReceived())
                .changeGiven(payment.getChangeGiven())
                .paidAt(payment.getPaidAt())
                .paidById(payment.getPaidBy().getId())
                .paidByUsername(payment.getPaidBy().getUsername())
                .build();
    }

    private List<ValidatedPayment> validatePayments(PayTicketRequestDto dto, BigDecimal total) {
        boolean legacy = dto.getPaymentMethod() != null;
        boolean paymentsSupplied = dto.getPayments() != null;
        boolean composed = paymentsSupplied && !dto.getPayments().isEmpty();
        if (legacy && paymentsSupplied) {
            throw new BusinessRuleException("PAYMENT_FORMAT_CONFLICT", "No se pueden mezclar los formatos de pago");
        }
        if (!legacy && !composed) {
            throw new BusinessRuleException("PAYMENT_COMPONENTS_REQUIRED", "Debes enviar un método o componentes de pago");
        }
        if (legacy) {
            if (dto.getPaymentMethod() == PaymentMethod.MIXED) {
                throw new BusinessRuleException("MIXED_METHOD_NOT_ALLOWED_AS_COMPONENT", "MIXED requiere componentes CASH y CARD");
            }
            BigDecimal normalizedTotal = money(total);
            return List.of(new ValidatedPayment(
                    dto.getPaymentMethod(), normalizedTotal,
                    dto.getPaymentMethod() == PaymentMethod.CASH ? normalizedTotal : null,
                    dto.getPaymentMethod() == PaymentMethod.CASH ? money(BigDecimal.ZERO) : null));
        }

        List<PaymentComponentRequestDto> components = dto.getPayments();
        if (components.size() < 1 || components.size() > 2) {
            throw new BusinessRuleException("PAYMENT_COMPONENTS_REQUIRED", "El pago debe contener uno o dos componentes");
        }
        Set<PaymentMethod> methods = new HashSet<>();
        List<ValidatedPayment> result = new java.util.ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (PaymentComponentRequestDto component : components) {
            PaymentMethod method = component.getMethod();
            if (method == null) {
                throw new BusinessRuleException("PAYMENT_COMPONENTS_REQUIRED", "Cada componente requiere método");
            }
            if (method == PaymentMethod.MIXED) {
                throw new BusinessRuleException("MIXED_METHOD_NOT_ALLOWED_AS_COMPONENT", "MIXED no es un componente de pago");
            }
            if (!methods.add(method)) {
                throw new BusinessRuleException("PAYMENT_METHOD_DUPLICATED", "No se puede repetir el método de pago");
            }
            BigDecimal amount = component.getAmount();
            if (amount == null && components.size() == 1) {
                amount = total;
            }
            validateMoney(amount);
            amount = money(amount);
            if (amount.signum() <= 0) {
                throw new BusinessRuleException("INVALID_PAYMENT_AMOUNT", "El importe debe ser positivo");
            }

            BigDecimal cashReceived = null;
            BigDecimal changeGiven = null;
            if (method == PaymentMethod.CARD) {
                if (component.getCashReceived() != null || component.getChangeGiven() != null) {
                    throw new BusinessRuleException("CASH_FIELDS_NOT_ALLOWED_FOR_CARD", "CARD no admite campos de efectivo");
                }
            } else {
                if (component.getChangeGiven() != null) {
                    throw new BusinessRuleException("CLIENT_CHANGE_NOT_ALLOWED", "El cambio lo calcula el servidor");
                }
                if (component.getCashReceived() == null) {
                    throw new BusinessRuleException("CASH_RECEIVED_REQUIRED", "CASH requiere cashReceived");
                }
                validateMoney(component.getCashReceived());
                cashReceived = money(component.getCashReceived());
                if (cashReceived.signum() < 0) {
                    throw new BusinessRuleException("INVALID_PAYMENT_AMOUNT", "El efectivo recibido no puede ser negativo");
                }
                if (cashReceived.compareTo(amount) < 0) {
                    throw new BusinessRuleException("INSUFFICIENT_CASH", "El efectivo recibido es insuficiente");
                }
                changeGiven = money(cashReceived.subtract(amount));
            }
            sum = sum.add(amount);
            result.add(new ValidatedPayment(method, amount, cashReceived, changeGiven));
        }
        if (money(sum).compareTo(money(total)) != 0) {
            throw new BusinessRuleException("PAYMENT_TOTAL_MISMATCH", "La suma de pagos no coincide con el total del ticket");
        }
        return result;
    }

    private void validateMoney(BigDecimal value) {
        if (value == null) {
            throw new BusinessRuleException("PAYMENT_COMPONENTS_REQUIRED", "Falta un importe obligatorio");
        }
        MoneyPolicy.requireValid(value, "INVALID_MONETARY_SCALE", "Los importes admiten como máximo dos decimales");
    }

    private BigDecimal money(BigDecimal value) {
        return MoneyPolicy.normalize(value);
    }

    private Map<String, Object> paymentAuditMetadata(Ticket ticket, List<Payment> payments, User actor) {
        List<Map<String, Object>> breakdown = payments.stream().map(payment -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("method", payment.getMethod());
            item.put("amount", payment.getAmount());
            item.put("cashReceived", payment.getCashReceived());
            item.put("changeGiven", payment.getChangeGiven());
            return item;
        }).toList();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("paymentMethod", ticket.getPaymentMethod());
        metadata.put("payments", breakdown);
        metadata.put("total", ticket.getTotal());
        metadata.put("actorUserId", actor.getId());
        metadata.put("paidById", actor.getId());
        metadata.put("paidByUsername", actor.getUsername());
        metadata.put("originCashSessionId", ticket.getOriginCashSession() == null
                ? null : ticket.getOriginCashSession().getId());
        CashSession paymentSession = payments.get(0).getCashSession();
        metadata.put("paymentCashSessionId", paymentSession.getId());
        metadata.put("paymentCashRegisterId", paymentSession.getCashRegister().getId());
        metadata.put("paymentCashRegisterName", paymentSession.getCashRegister().getName());
        metadata.put("paymentSessionResponsibleId", paymentSession.getOpenedBy().getId());
        metadata.put("paymentSessionResponsibleUsername", paymentSession.getOpenedBy().getUsername());
        return metadata;
    }

    private record ValidatedPayment(
            PaymentMethod method, BigDecimal amount, BigDecimal cashReceived, BigDecimal changeGiven) {}

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
        if (ticket.getOriginCashSession() != null) {
            CashSession origin = ticket.getOriginCashSession();
            snapshot.put("originCashSessionId", origin.getId());
            snapshot.put("originCashRegisterId", origin.getCashRegister().getId());
            snapshot.put("originCashRegisterName", origin.getCashRegister().getName());
            snapshot.put("sessionResponsibleId", origin.getOpenedBy().getId());
            snapshot.put("sessionResponsibleUsername", origin.getOpenedBy().getUsername());
            snapshot.put("createdById", ticket.getCreatedBy().getId());
            snapshot.put("createdByUsername", ticket.getCreatedBy().getUsername());
        }
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
        if (LocalDate.MAX.equals(to)) {
            throw new BusinessRuleException("INVALID_DATE_RANGE", "La fecha 'to' está fuera del rango permitido");
        }
    }

    private static class DailyAccumulator {
        private long ticketCount = 0L;
        private BigDecimal totalSales = BigDecimal.ZERO;
    }
}
