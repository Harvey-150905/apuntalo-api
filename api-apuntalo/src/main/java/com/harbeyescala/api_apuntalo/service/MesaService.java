package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.dto.MesaRequestDto;
import com.harbeyescala.api_apuntalo.dto.MesaResponseDto;
import com.harbeyescala.api_apuntalo.entity.Mesa;
import com.harbeyescala.api_apuntalo.entity.Negocio;
import com.harbeyescala.api_apuntalo.entity.enums.AuditAction;
import com.harbeyescala.api_apuntalo.entity.enums.AuditEntityType;
import com.harbeyescala.api_apuntalo.entity.enums.MesaStatus;
import com.harbeyescala.api_apuntalo.entity.enums.TicketStatus;
import com.harbeyescala.api_apuntalo.exception.ConflictException;
import com.harbeyescala.api_apuntalo.exception.DuplicateResourceException;
import com.harbeyescala.api_apuntalo.exception.ResourceNotFoundException;
import com.harbeyescala.api_apuntalo.repository.MesaRepository;
import com.harbeyescala.api_apuntalo.repository.NegocioRepository;
import com.harbeyescala.api_apuntalo.repository.TicketRepository;
import com.harbeyescala.api_apuntalo.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * CRUD de mesas de la Store activa. Fase 9 (F9.6): las altas/modificaciones
 * quedan auditadas y el borrado físico se deshabilita en favor de
 * activar/desactivar vía {@code PATCH /api/mesas/{id}/status}. Una mesa no
 * puede desactivarse mientras esté {@code OCCUPIED} o tenga un ticket
 * {@code OPEN} asociado.
 */
@Service
public class MesaService {

    private final MesaRepository mesaRepository;
    private final NegocioRepository negocioRepository;
    private final TicketRepository ticketRepository;
    private final AuditEventService auditEventService;
    private final ActiveStoreContext storeContext;

    public MesaService(MesaRepository mesaRepository,
                       NegocioRepository negocioRepository,
                       TicketRepository ticketRepository,
                       AuditEventService auditEventService,
                       ActiveStoreContext storeContext) {
        this.mesaRepository = mesaRepository;
        this.negocioRepository = negocioRepository;
        this.ticketRepository = ticketRepository;
        this.auditEventService = auditEventService;
        this.storeContext = storeContext;
    }

    @Transactional
    public MesaResponseDto create(MesaRequestDto dto) {
        Long negocioId = SecurityUtils.getNegocioId();

        Long storeId = storeContext.storeId();
        if (mesaRepository.existsByNumeroAndNegocioIdAndStoreId(dto.getNumero(), negocioId, storeId)) {
            throw new DuplicateResourceException("Ya existe una mesa con ese número");
        }

        Negocio negocio = negocioRepository.findById(negocioId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado"));

        Mesa mesa = Mesa.builder()
                .numero(dto.getNumero())
                .status(MesaStatus.FREE)
                .activa(dto.getActiva() != null ? dto.getActiva() : true)
                .negocio(negocio)
                .store(storeContext.requireStore())
                .build();

        Mesa saved = mesaRepository.save(mesa);

        auditEventService.recordSuccess(AuditEntityType.MESA, saved.getId(), AuditAction.MESA_CREATED,
                null, Map.of("numero", saved.getNumero(), "activa", saved.getActiva()));

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<MesaResponseDto> findAll() {
        Long negocioId = SecurityUtils.getNegocioId();
        return mesaRepository.findByNegocioIdAndStoreId(negocioId, storeContext.storeId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MesaResponseDto> findAllActivas() {
        Long negocioId = SecurityUtils.getNegocioId();
        return mesaRepository.findByNegocioIdAndStoreIdAndActivaTrue(negocioId, storeContext.storeId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MesaResponseDto findById(Long id) {
        Long negocioId = SecurityUtils.getNegocioId();

        Mesa mesa = mesaRepository.findByIdAndNegocioIdAndStoreId(id, negocioId, storeContext.storeId())
                .orElseThrow(() -> new ResourceNotFoundException("Mesa no encontrada"));

        return toResponse(mesa);
    }

    @Transactional
    public MesaResponseDto update(Long id, MesaRequestDto dto) {
        Long negocioId = SecurityUtils.getNegocioId();

        Mesa mesa = mesaRepository.findByIdAndNegocioIdAndStoreId(id, negocioId, storeContext.storeId())
                .orElseThrow(() -> new ResourceNotFoundException("Mesa no encontrada"));

        if (mesaRepository.existsByNumeroAndNegocioIdAndStoreIdAndIdNot(dto.getNumero(), negocioId, storeContext.storeId(), id)) {
            throw new DuplicateResourceException("Ya existe otra mesa con ese número");
        }

        Map<String, Object> before = Map.of("numero", mesa.getNumero(), "activa", mesa.getActiva());

        mesa.setNumero(dto.getNumero());

        if (dto.getActiva() != null) {
            mesa.setActiva(dto.getActiva());
        }

        Mesa saved = mesaRepository.save(mesa);

        auditEventService.recordSuccess(AuditEntityType.MESA, saved.getId(), AuditAction.MESA_UPDATED,
                before, Map.of("numero", saved.getNumero(), "activa", saved.getActiva()));

        return toResponse(saved);
    }

    /**
     * Activa/desactiva una mesa (Fase 9, F9.6). Bloquea la desactivación si
     * la mesa está {@code OCCUPIED} o tiene un ticket {@code OPEN} asociado
     * en la Store activa.
     */
    @Transactional
    public MesaResponseDto setActive(Long id, boolean active) {
        Long negocioId = SecurityUtils.getNegocioId();
        Long storeId = storeContext.storeId();

        Mesa mesa = mesaRepository.findByIdAndNegocioIdAndStoreIdForUpdate(id, negocioId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa no encontrada"));

        boolean previous = Boolean.TRUE.equals(mesa.getActiva());
        if (previous == active) {
            return toResponse(mesa);
        }

        if (!active) {
            boolean hasOpenTicket = mesa.getStatus() == MesaStatus.OCCUPIED
                    || ticketRepository.existsByMesaIdAndNegocioIdAndStoreIdAndStatus(
                            id, negocioId, storeId, TicketStatus.OPEN);
            if (hasOpenTicket) {
                throw new ConflictException("MESA_HAS_OPEN_TICKET",
                        "No se puede desactivar una mesa con un ticket abierto");
            }
        }

        mesa.setActiva(active);
        Mesa saved = mesaRepository.save(mesa);

        auditEventService.recordSuccess(AuditEntityType.MESA, id,
                active ? AuditAction.MESA_ACTIVATED : AuditAction.MESA_DEACTIVATED,
                Map.of("activa", previous), Map.of("activa", active));

        return toResponse(saved);
    }

    /**
     * @deprecated Fase 9 (F9.6): el borrado físico de una mesa no está
     * permitido; usa {@link #setActive(Long, boolean)} vía
     * {@code PATCH /api/mesas/{id}/status}.
     */
    @Deprecated
    public void delete(Long id) {
        throw new ConflictException("PHYSICAL_DELETE_DISABLED",
                "El borrado físico de una mesa está deshabilitado; usa desactivación");
    }

    private MesaResponseDto toResponse(Mesa mesa) {
        return MesaResponseDto.builder()
                .id(mesa.getId())
                .numero(mesa.getNumero())
                .status(mesa.getStatus())
                .activa(mesa.getActiva())
                .build();
    }
}
