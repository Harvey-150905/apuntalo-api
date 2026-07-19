package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.dto.CashRegisterNameRequestDto;
import com.harbeyescala.api_apuntalo.dto.CashRegisterResponseDto;
import com.harbeyescala.api_apuntalo.dto.CashRegisterStatusRequestDto;
import com.harbeyescala.api_apuntalo.entity.CashRegister;
import com.harbeyescala.api_apuntalo.entity.Negocio;
import com.harbeyescala.api_apuntalo.entity.User;
import com.harbeyescala.api_apuntalo.entity.enums.AuditAction;
import com.harbeyescala.api_apuntalo.entity.enums.AuditEntityType;
import com.harbeyescala.api_apuntalo.exception.BadRequestException;
import com.harbeyescala.api_apuntalo.exception.ConflictException;
import com.harbeyescala.api_apuntalo.exception.ResourceNotFoundException;
import com.harbeyescala.api_apuntalo.repository.CashRegisterRepository;
import com.harbeyescala.api_apuntalo.repository.NegocioRepository;
import com.harbeyescala.api_apuntalo.repository.UserRepository;
import com.harbeyescala.api_apuntalo.repository.CashSessionRepository;
import com.harbeyescala.api_apuntalo.entity.enums.CashSessionStatus;
import com.harbeyescala.api_apuntalo.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CashRegisterService {
    private static final int MAX_NAME_LENGTH = 100;

    private final CashRegisterRepository repository;
    private final NegocioRepository negocioRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final AuditEventService auditEventService;
    private final CashSessionRepository cashSessionRepository;
    private final Clock clock;
    private final ActiveStoreContext storeContext;

    public CashRegisterService(CashRegisterRepository repository, NegocioRepository negocioRepository,
                               UserRepository userRepository, CurrentUser currentUser,
                               AuditEventService auditEventService,
                               CashSessionRepository cashSessionRepository, Clock clock, ActiveStoreContext storeContext) {
        this.repository = repository;
        this.negocioRepository = negocioRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.auditEventService = auditEventService;
        this.cashSessionRepository = cashSessionRepository;
        this.clock = clock;
        this.storeContext=storeContext;
    }

    @Transactional
    public CashRegisterResponseDto create(CashRegisterNameRequestDto request) {
        Long tenantId = currentUser.getTenantId();
        String name = cleanName(request.getName());
        String normalized = normalize(name);
        Long storeId=storeContext.storeId();
        if (repository.existsByNegocioIdAndStoreIdAndNormalizedName(tenantId,storeId, normalized)) {
            throw duplicateName();
        }
        Negocio negocio = negocioRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado"));
        User actor = currentActor(tenantId);
        LocalDateTime now = LocalDateTime.now(clock);
        CashRegister register = CashRegister.builder()
                .negocio(negocio).store(storeContext.requireStore()).name(name).normalizedName(normalized).active(true)
                .createdAt(now).updatedAt(now).createdBy(actor).updatedBy(actor).build();
        try {
            register = repository.saveAndFlush(register);
        } catch (DataIntegrityViolationException ex) {
            throw duplicateName();
        }
        auditEventService.recordSuccess(AuditEntityType.CASH_REGISTER, register.getId(),
                AuditAction.CASH_REGISTER_CREATED, null,
                Map.of("name", register.getName(), "active", true));
        return toResponse(register);
    }

    @Transactional(readOnly = true)
    public List<CashRegisterResponseDto> findAll() {
        return repository.findByNegocioIdAndStoreIdOrderByNormalizedNameAscIdAsc(currentUser.getTenantId(),storeContext.storeId())
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<CashRegisterResponseDto> findActive() {
        return repository.findByNegocioIdAndStoreIdAndActiveTrueOrderByNormalizedNameAscIdAsc(currentUser.getTenantId(),storeContext.storeId())
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CashRegisterResponseDto findById(Long id) {
        return toResponse(findTenantRegister(id));
    }

    @Transactional
    public CashRegisterResponseDto rename(Long id, CashRegisterNameRequestDto request) {
        Long tenantId = currentUser.getTenantId();
        CashRegister register = findTenantRegister(id);
        String name = cleanName(request.getName());
        if (register.getName().equals(name)) {
            return toResponse(register);
        }
        String normalized = normalize(name);
        if (repository.existsByNegocioIdAndStoreIdAndNormalizedNameAndIdNot(tenantId,storeContext.storeId(), normalized, id)) {
            throw duplicateName();
        }
        String previous = register.getName();
        register.setName(name);
        register.setNormalizedName(normalized);
        touch(register, tenantId);
        try {
            register = repository.saveAndFlush(register);
        } catch (DataIntegrityViolationException ex) {
            throw duplicateName();
        }
        auditEventService.recordSuccess(AuditEntityType.CASH_REGISTER, register.getId(),
                AuditAction.CASH_REGISTER_RENAMED,
                Map.of("name", previous), Map.of("name", register.getName()));
        return toResponse(register);
    }

    @Transactional
    public CashRegisterResponseDto updateStatus(Long id, CashRegisterStatusRequestDto request) {
        Long tenantId = currentUser.getTenantId();
        negocioRepository.findByIdForUpdate(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado"));
        User actor = userRepository.findByIdAndNegocioIdForUpdate(currentUser.getUserId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        CashRegister register = repository.findByIdAndNegocioIdAndStoreIdForUpdate(id, tenantId,storeContext.storeId())
                .orElseThrow(com.harbeyescala.api_apuntalo.exception.CashRegisterNotFoundException::new);
        boolean active = Boolean.TRUE.equals(request.getActive());
        if (!active && cashSessionRepository.existsByCashRegisterIdAndNegocioIdAndStoreIdAndStatus(
                id, tenantId, storeContext.storeId(), CashSessionStatus.OPEN)) {
            throw new ConflictException("CASH_REGISTER_HAS_OPEN_SESSION",
                    "No se puede desactivar una caja con una sesión abierta");
        }
        if (Boolean.TRUE.equals(register.getActive()) == active) {
            return toResponse(register);
        }
        boolean previous = Boolean.TRUE.equals(register.getActive());
        register.setActive(active);
        touch(register, actor);
        repository.save(register);
        auditEventService.recordSuccess(AuditEntityType.CASH_REGISTER, register.getId(),
                active ? AuditAction.CASH_REGISTER_ACTIVATED : AuditAction.CASH_REGISTER_DEACTIVATED,
                Map.of("name", register.getName(), "active", previous),
                Map.of("name", register.getName(), "active", active));
        return toResponse(register);
    }

    private CashRegister findTenantRegister(Long id) {
        return repository.findByIdAndNegocioIdAndStoreId(id, currentUser.getTenantId(),storeContext.storeId())
                .orElseThrow(() -> new com.harbeyescala.api_apuntalo.exception.CashRegisterNotFoundException());
    }

    private void touch(CashRegister register, Long tenantId) {
        register.setUpdatedAt(LocalDateTime.now(clock));
        register.setUpdatedBy(currentActor(tenantId));
    }

    private void touch(CashRegister register, User actor) {
        register.setUpdatedAt(LocalDateTime.now(clock));
        register.setUpdatedBy(actor);
    }

    private User currentActor(Long tenantId) {
        return userRepository.findByIdAndNegocioId(currentUser.getUserId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private String cleanName(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new BadRequestException("CASH_REGISTER_NAME_REQUIRED", "El nombre de la caja es obligatorio");
        }
        String name = raw.trim();
        if (name.length() > MAX_NAME_LENGTH) {
            throw new BadRequestException("CASH_REGISTER_NAME_TOO_LONG", "El nombre no puede superar 100 caracteres");
        }
        return name;
    }

    private String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private ConflictException duplicateName() {
        return new ConflictException("CASH_REGISTER_NAME_ALREADY_EXISTS", "Ya existe una caja con ese nombre");
    }

    private CashRegisterResponseDto toResponse(CashRegister register) {
        return CashRegisterResponseDto.builder()
                .id(register.getId()).name(register.getName()).active(register.getActive())
                .createdAt(register.getCreatedAt()).updatedAt(register.getUpdatedAt()).build();
    }
}
