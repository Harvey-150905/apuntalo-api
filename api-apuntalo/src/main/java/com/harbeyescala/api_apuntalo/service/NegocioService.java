package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.dto.NegocioRequestDto;
import com.harbeyescala.api_apuntalo.dto.NegocioResponseDto;
import com.harbeyescala.api_apuntalo.entity.Negocio;
import com.harbeyescala.api_apuntalo.entity.enums.AuditAction;
import com.harbeyescala.api_apuntalo.entity.enums.AuditEntityType;
import com.harbeyescala.api_apuntalo.entity.enums.CashSessionStatus;
import com.harbeyescala.api_apuntalo.entity.enums.TicketStatus;
import com.harbeyescala.api_apuntalo.exception.ConflictException;
import com.harbeyescala.api_apuntalo.repository.CashSessionRepository;
import com.harbeyescala.api_apuntalo.repository.NegocioRepository;
import com.harbeyescala.api_apuntalo.repository.TicketRepository;
import com.harbeyescala.api_apuntalo.security.SecurityUtils;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.harbeyescala.api_apuntalo.exception.ResourceNotFoundException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class NegocioService {

    private final NegocioRepository negocioRepository;
    private final CashSessionRepository cashSessionRepository;
    private final TicketRepository ticketRepository;
    private final AuditEventService auditEventService;

    public NegocioService(NegocioRepository negocioRepository,
                          CashSessionRepository cashSessionRepository,
                          TicketRepository ticketRepository,
                          AuditEventService auditEventService) {
        this.negocioRepository = negocioRepository;
        this.cashSessionRepository = cashSessionRepository;
        this.ticketRepository = ticketRepository;
        this.auditEventService = auditEventService;
    }

    public NegocioResponseDto save(NegocioRequestDto dto) {
        Negocio negocio = Negocio.builder()
                .nombre(dto.getNombre())
                .build();

        Negocio savedNegocio = negocioRepository.save(negocio);

        return mapToResponseDto(savedNegocio);
    }

    public List<NegocioResponseDto> findAll() {
        if (isSuperAdmin()) {
            return negocioRepository.findAll()
                    .stream()
                    .map(this::mapToResponseDto)
                    .toList();
        }

        Long negocioId = SecurityUtils.getNegocioId();

        return negocioRepository.findById(negocioId)
                .stream()
                .map(this::mapToResponseDto)
                .toList();
    }

    public Optional<NegocioResponseDto> findById(Long id) {
        if (isSuperAdmin()) {
            return negocioRepository.findById(id)
                    .map(this::mapToResponseDto);
        }

        Long negocioId = SecurityUtils.getNegocioId();

        if (!negocioId.equals(id)) {
            return Optional.empty();
        }

        return negocioRepository.findById(id)
                .map(this::mapToResponseDto);
    }

    /**
     * @deprecated Fase 9 (F9.8): el borrado físico de un negocio no está
     * permitido; los negocios se conservan por historia y se
     * activan/desactivan mediante {@link #setActive(Long, boolean)}.
     */
    @Deprecated
    public void deleteById(Long id) {
        throw new ConflictException(
                "NEGOCIO_PHYSICAL_DELETE_DISABLED",
                "El borrado físico de un negocio está deshabilitado; usa la desactivación");
    }

    /**
     * Activa o desactiva un negocio (Fase 9, F9.8; solo SUPER_ADMIN). Al
     * desactivar, bloquea si hay sesiones de caja o tickets abiertos en
     * cualquier Store del negocio. La desactivación se aplica inmediatamente:
     * el resolver de tokens rechaza en la siguiente petición cualquier JWT de
     * un negocio inactivo.
     */
    @Transactional
    public NegocioResponseDto setActive(Long id, boolean active) {
        Negocio negocio = negocioRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado"));

        boolean previous = Boolean.TRUE.equals(negocio.getActivo());
        if (previous == active) {
            return mapToResponseDto(negocio);
        }

        if (!active) {
            if (cashSessionRepository.existsByNegocioIdAndStatus(id, CashSessionStatus.OPEN)) {
                throw new ConflictException(
                        "NEGOCIO_HAS_OPEN_CASH_SESSIONS",
                        "No se puede desactivar un negocio con sesiones de caja abiertas");
            }
            if (ticketRepository.existsByNegocioIdAndStatus(id, TicketStatus.OPEN)) {
                throw new ConflictException(
                        "NEGOCIO_HAS_OPEN_TICKETS",
                        "No se puede desactivar un negocio con tickets abiertos");
            }
        }

        negocio.setActivo(active);
        Negocio saved = negocioRepository.save(negocio);

        auditEventService.recordSuccessForTenant(id, null, AuditEntityType.NEGOCIO, id,
                active ? AuditAction.NEGOCIO_ACTIVATED : AuditAction.NEGOCIO_DEACTIVATED,
                Map.of("activo", previous), Map.of("activo", active), null);

        return mapToResponseDto(saved);
    }
    
    private NegocioResponseDto mapToResponseDto(Negocio negocio) {
        return NegocioResponseDto.builder()
                .id(negocio.getId())
                .nombre(negocio.getNombre())
                .build();
    }
    public NegocioResponseDto update(Long id, NegocioRequestDto dto) {
        Negocio negocio = negocioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado"));

        negocio.setNombre(dto.getNombre());

        Negocio updatedNegocio = negocioRepository.save(negocio);

        return mapToResponseDto(updatedNegocio);
    }
    public List<NegocioResponseDto> findActivos() {
        return negocioRepository.findByActivoTrue()
                .stream()
                .map(this::mapToResponseDto)
                .toList();
    }

    private boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(SecurityUtils.getRole());
    }
}