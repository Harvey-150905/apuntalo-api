package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.dto.CashSessionResponseDto;
import com.harbeyescala.api_apuntalo.dto.OpenCashSessionRequestDto;
import com.harbeyescala.api_apuntalo.dto.CashSessionSummaryDto;
import com.harbeyescala.api_apuntalo.dto.PendingCashSessionTicketDto;
import com.harbeyescala.api_apuntalo.entity.*;
import com.harbeyescala.api_apuntalo.entity.enums.AuditAction;
import com.harbeyescala.api_apuntalo.entity.enums.AuditEntityType;
import com.harbeyescala.api_apuntalo.entity.enums.CashSessionStatus;
import com.harbeyescala.api_apuntalo.entity.enums.CashSessionCloseMode;
import com.harbeyescala.api_apuntalo.exception.*;
import com.harbeyescala.api_apuntalo.repository.*;
import com.harbeyescala.api_apuntalo.repository.projection.CashSessionSummaryProjection;
import com.harbeyescala.api_apuntalo.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Clock;
import java.util.List;
import java.util.Map;

@Service
public class CashSessionService {
    private static final BigDecimal MAX_OPENING_FLOAT = new BigDecimal("99999999.99");

    private final CashSessionRepository sessionRepository;
    private final CashRegisterRepository registerRepository;
    private final NegocioRepository negocioRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final AuditEventService auditEventService;
    private final TicketRepository ticketRepository;
    private final Clock clock;
    private final ActiveStoreContext storeContext;

    public CashSessionService(CashSessionRepository sessionRepository, CashRegisterRepository registerRepository,
                              NegocioRepository negocioRepository, UserRepository userRepository,
                              CurrentUser currentUser, AuditEventService auditEventService,
                              TicketRepository ticketRepository, Clock clock, ActiveStoreContext storeContext) {
        this.sessionRepository = sessionRepository;
        this.registerRepository = registerRepository;
        this.negocioRepository = negocioRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.auditEventService = auditEventService;
        this.ticketRepository = ticketRepository;
        this.clock = clock;
        this.storeContext=storeContext;
    }

    @Transactional
    public CashSessionResponseDto open(OpenCashSessionRequestDto request) {
        Long tenantId = currentUser.getTenantId();
        Long userId = currentUser.getUserId();
        Long storeId = storeContext.storeId();
        BigDecimal openingFloat = validateOpeningFloat(request.getOpeningFloat());
        if (request.getCashRegisterId() == null) {
            throw new BadRequestException("CASH_REGISTER_ID_REQUIRED", "La caja es obligatoria");
        }

        // Orden único F6.3: negocio -> usuario responsable -> caja.
        Negocio negocio = negocioRepository.findByIdForUpdate(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado"));
        if (!Boolean.TRUE.equals(negocio.getActivo())) {
            throw new BusinessRuleException("NEGOCIO_INACTIVE", "El negocio está inactivo");
        }
        User responsible = userRepository.findByIdAndNegocioIdForUpdate(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        if (!Boolean.TRUE.equals(responsible.getActivo())) {
            throw new BusinessRuleException("USER_INACTIVE", "El usuario está inactivo");
        }

        CashRegister register = registerRepository
                .findByIdAndNegocioIdAndStoreIdForUpdate(request.getCashRegisterId(), tenantId,storeId)
                .orElseThrow(CashRegisterNotFoundException::new);
        if (!Boolean.TRUE.equals(register.getActive())) {
            throw new BusinessRuleException("CASH_REGISTER_INACTIVE", "La caja está desactivada");
        }
        if (sessionRepository.existsByCashRegisterIdAndNegocioIdAndStoreIdAndStatus(
                register.getId(), tenantId, storeId, CashSessionStatus.OPEN)) {
            throw new ConflictException("CASH_REGISTER_ALREADY_OPEN", "La caja ya tiene una sesión abierta");
        }
        if (sessionRepository.existsByOpenedByIdAndNegocioIdAndStatus(
                userId, tenantId, CashSessionStatus.OPEN)) {
            throw new ConflictException("USER_ALREADY_RESPONSIBLE_FOR_OPEN_SESSION",
                    "El usuario ya es responsable de una sesión abierta");
        }

        LocalDateTime openedAt = LocalDateTime.now(clock);
        CashSession session = CashSession.builder()
                .negocio(negocio).store(register.getStore()).cashRegister(register).status(CashSessionStatus.OPEN)
                .openingFloat(openingFloat).openedBy(responsible)
                .reconciliationRequired(Boolean.TRUE.equals(register.getStore().getCashReconciliationEnabled()))
                .openedAt(openedAt).createdAt(openedAt).version(0L).build();
        try {
            session = sessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException ex) {
            throw translateOpenConflict(ex);
        }

        auditEventService.recordSuccess(AuditEntityType.CASH_SESSION, session.getId(),
                AuditAction.CASH_SESSION_OPENED, null,
                Map.of("status", CashSessionStatus.OPEN, "openingFloat", openingFloat),
                Map.of("cashSessionId", session.getId(), "cashRegisterId", register.getId(),
                        "cashRegisterName", register.getName(), "openingFloat", openingFloat,
                        "responsibleUserId", responsible.getId(),
                        "responsibleUsername", responsible.getUsername(),
                        "reconciliationRequired", session.getReconciliationRequired(),
                        "status", CashSessionStatus.OPEN, "openedAt", openedAt));
        return toResponse(session);
    }

    @Transactional(readOnly = true)
    public CashSessionResponseDto findMyOpen() {
        return sessionRepository.findByOpenedByIdAndNegocioIdAndStoreIdAndStatus(
                        currentUser.getUserId(), currentUser.getTenantId(),storeContext.storeId(), CashSessionStatus.OPEN)
                .map(this::toResponse).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<CashSessionResponseDto> findOpen() {
        return sessionRepository.findByNegocioIdAndStoreIdAndStatusOrderByOpenedAtAscIdAsc(
                        currentUser.getTenantId(),storeContext.storeId(), CashSessionStatus.OPEN)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CashSessionResponseDto findById(Long id) {
        return sessionRepository.findByIdAndNegocioIdAndStoreId(id, currentUser.getTenantId(),storeContext.storeId())
                .map(this::toResponse).orElseThrow(CashSessionNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public CashSessionSummaryDto findSummary(Long id) {
        return sessionRepository.findSummary(currentUser.getTenantId(), storeContext.storeId(), id)
                .map(this::toSummary).orElseThrow(CashSessionNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public List<CashSessionSummaryDto> findOpenSummaries() {
        return sessionRepository.findOpenSummaries(currentUser.getTenantId(), storeContext.storeId())
                .stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<PendingCashSessionTicketDto> findPendingTickets(Long id) {
        Long tenantId = currentUser.getTenantId();
        if (!sessionRepository.existsByIdAndNegocioIdAndStoreId(id, tenantId,storeContext.storeId())) {
            throw new CashSessionNotFoundException();
        }
        return ticketRepository.findPendingByOriginSession(tenantId, id).stream()
                .map(p -> PendingCashSessionTicketDto.builder()
                        .ticketId(p.getTicketId()).commercialNumber(p.getCommercialNumber())
                        .mesaId(p.getMesaId()).mesaNumero(p.getMesaNumero()).createdAt(p.getCreatedAt())
                        .createdById(p.getCreatedById()).createdByUsername(p.getCreatedByUsername())
                        .total(p.getTotal()).activeLineCount(p.getActiveLineCount()).build())
                .toList();
    }

    private CashSessionSummaryDto toSummary(CashSessionSummaryProjection p) {
        return CashSessionSummaryDto.builder()
                .sessionId(p.getSessionId()).status(CashSessionStatus.valueOf(p.getStatus())).cashRegisterId(p.getCashRegisterId())
                .cashRegisterName(p.getCashRegisterName()).responsibleUserId(p.getResponsibleUserId())
                .responsibleUsername(p.getResponsibleUsername()).openedAt(p.getOpenedAt())
                .openingFloat(p.getOpeningFloat()).reconciliationRequired(p.getReconciliationRequired())
                .cashSales(p.getCashSales()).cardSales(p.getCardSales()).totalSales(p.getTotalSales())
                .expectedCash(p.getExpectedCash()).ticketCount(p.getTicketCount())
                .cashIn(p.getCashIn()).cashOut(p.getCashOut()).closedAt(p.getClosedAt())
                .closedById(p.getClosedById()).closedByUsername(p.getClosedByUsername())
                .closeMode(p.getCloseMode() == null ? null : CashSessionCloseMode.valueOf(p.getCloseMode()))
                .expectedCashAtClose(p.getExpectedCashAtClose()).countedCash(p.getCountedCash())
                .difference(p.getDifference()).pendingTicketCountAtClose(p.getPendingTicketCountAtClose())
                .pendingTicketAmountAtClose(p.getPendingTicketAmountAtClose())
                .pendingTicketsAcknowledged(p.getPendingTicketsAcknowledged())
                .cashPaymentCount(p.getCashPaymentCount()).cardPaymentCount(p.getCardPaymentCount())
                .ticketsOpenedCount(p.getTicketsOpenedCount()).openOriginTicketsCount(p.getOpenOriginTicketsCount())
                .openOriginTicketsAmount(p.getOpenOriginTicketsAmount())
                .ticketsOriginatedHerePaidHereCount(p.getTicketsOriginatedHerePaidHereCount())
                .ticketsOriginatedHerePaidElsewhereCount(p.getTicketsOriginatedHerePaidElsewhereCount())
                .ticketsFromOtherSessionsPaidHereCount(p.getTicketsFromOtherSessionsPaidHereCount()).build();
    }

    private BigDecimal validateOpeningFloat(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(MoneyPolicy.MAX_VALUE) > 0) {
            throw new BusinessRuleException("INVALID_OPENING_FLOAT", "El fondo inicial no es válido");
        }
        return MoneyPolicy.requireValid(value, "INVALID_MONETARY_SCALE",
                "El fondo inicial admite como máximo dos decimales");
    }

    private ConflictException translateOpenConflict(DataIntegrityViolationException ex) {
        String detail = ex.getMostSpecificCause().getMessage();
        if (detail != null && detail.contains("uk_cash_sessions_open_responsible")) {
            return new ConflictException("USER_ALREADY_RESPONSIBLE_FOR_OPEN_SESSION",
                    "El usuario ya es responsable de una sesión abierta");
        }
        return new ConflictException("CASH_REGISTER_ALREADY_OPEN", "La caja ya tiene una sesión abierta");
    }

    private CashSessionResponseDto toResponse(CashSession session) {
        return CashSessionResponseDto.builder()
                .id(session.getId()).status(session.getStatus()).openingFloat(session.getOpeningFloat())
                .reconciliationRequired(session.getReconciliationRequired())
                .openedAt(session.getOpenedAt()).cashRegisterId(session.getCashRegister().getId())
                .cashRegisterName(session.getCashRegister().getName())
                .responsibleUserId(session.getOpenedBy().getId())
                .responsibleUsername(session.getOpenedBy().getUsername()).build();
    }
}
