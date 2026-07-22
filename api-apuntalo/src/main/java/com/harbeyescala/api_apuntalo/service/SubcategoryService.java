package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.dto.SubcategoryRequestDto;
import com.harbeyescala.api_apuntalo.dto.SubcategoryResponseDto;
import com.harbeyescala.api_apuntalo.entity.Negocio;
import com.harbeyescala.api_apuntalo.entity.Subcategory;
import com.harbeyescala.api_apuntalo.entity.enums.AuditAction;
import com.harbeyescala.api_apuntalo.entity.enums.AuditEntityType;
import com.harbeyescala.api_apuntalo.repository.SubcategoryRepository;
import com.harbeyescala.api_apuntalo.security.SecurityUtils;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.harbeyescala.api_apuntalo.exception.DuplicateResourceException;
import com.harbeyescala.api_apuntalo.exception.ResourceNotFoundException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CRUD de subcategorías de la Store activa. Fase 9 (F9.7): las
 * altas/modificaciones quedan auditadas y el borrado deja de ser físico:
 * {@link #deleteById(Long)} desactiva la subcategoría (conserva historia y
 * referencias de productos existentes) en lugar de eliminar la fila.
 */
@Service
public class SubcategoryService {

    private final SubcategoryRepository subcategoryRepository;
    private final AuditEventService auditEventService;
    private final ActiveStoreContext storeContext;

    public SubcategoryService(SubcategoryRepository subcategoryRepository,
                              AuditEventService auditEventService,
                              ActiveStoreContext storeContext) {
        this.subcategoryRepository = subcategoryRepository;
        this.auditEventService = auditEventService;
        this.storeContext = storeContext;
    }

    @Transactional
    public SubcategoryResponseDto save(SubcategoryRequestDto dto) {
        Long negocioId = SecurityUtils.getNegocioId();

        if (subcategoryRepository.existsByNombreAndCategoryAndNegocioIdAndStoreId(
                dto.getNombre(),
                dto.getCategory(),
                negocioId, storeContext.storeId()
        )) {
            throw new DuplicateResourceException("La subcategoría ya existe en esa categoría");
        }

        Subcategory subcategory = Subcategory.builder()
                .nombre(dto.getNombre())
                .category(dto.getCategory())
                .activo(dto.getActivo() != null ? dto.getActivo() : true)
                .negocio(new Negocio(negocioId)) // 👈 IMPORTANTE
                .store(storeContext.requireStore())
                .build();

        Subcategory saved = subcategoryRepository.save(subcategory);

        auditEventService.recordSuccess(AuditEntityType.SUBCATEGORY, saved.getId(),
                AuditAction.SUBCATEGORY_CREATED, null,
                Map.of("nombre", saved.getNombre(), "category", saved.getCategory(), "activo", saved.getActivo()));

        return mapToResponseDto(saved);
    }

    @Transactional(readOnly = true)
    public List<SubcategoryResponseDto> findAll() {
        Long negocioId = SecurityUtils.getNegocioId();

        return subcategoryRepository.findByNegocioIdAndStoreId(negocioId, storeContext.storeId())
                .stream()
                .map(this::mapToResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<SubcategoryResponseDto> findById(Long id) {
        Long negocioId = SecurityUtils.getNegocioId();

        return subcategoryRepository.findByIdAndNegocioIdAndStoreId(id, negocioId, storeContext.storeId())
                .map(this::mapToResponseDto);
    }

    /**
     * @deprecated Fase 9 (F9.7): el borrado físico de una subcategoría con
     * historia no está permitido; usa {@link #setActive(Long, boolean)} vía
     * {@code PATCH /api/subcategories/{id}/status}. Se conserva por
     * compatibilidad con el endpoint {@code DELETE} existente, que ahora
     * desactiva en lugar de eliminar la fila.
     */
    @Deprecated
    @Transactional
    public void deleteById(Long id) {
        Long negocioId = SecurityUtils.getNegocioId();

        Subcategory subcategory = subcategoryRepository
                .findByIdAndNegocioIdAndStoreId(id, negocioId, storeContext.storeId())
                .orElseThrow(() -> new ResourceNotFoundException("Subcategoría no encontrada"));

        if (Boolean.FALSE.equals(subcategory.getActivo())) {
            return;
        }

        subcategory.setActivo(false);
        subcategoryRepository.save(subcategory);

        auditEventService.recordSuccess(AuditEntityType.SUBCATEGORY, id,
                AuditAction.SUBCATEGORY_DEACTIVATED,
                Map.of("activo", true), Map.of("activo", false));
    }

    /**
     * Activa/desactiva una subcategoría (Fase 9, F9.7).
     */
    @Transactional
    public SubcategoryResponseDto setActive(Long id, boolean active) {
        Long negocioId = SecurityUtils.getNegocioId();

        Subcategory subcategory = subcategoryRepository
                .findByIdAndNegocioIdAndStoreId(id, negocioId, storeContext.storeId())
                .orElseThrow(() -> new ResourceNotFoundException("Subcategoría no encontrada"));

        boolean previous = Boolean.TRUE.equals(subcategory.getActivo());
        if (previous == active) {
            return mapToResponseDto(subcategory);
        }

        subcategory.setActivo(active);
        Subcategory saved = subcategoryRepository.save(subcategory);

        auditEventService.recordSuccess(AuditEntityType.SUBCATEGORY, id,
                active ? AuditAction.SUBCATEGORY_ACTIVATED : AuditAction.SUBCATEGORY_DEACTIVATED,
                Map.of("activo", previous), Map.of("activo", active));

        return mapToResponseDto(saved);
    }

    private SubcategoryResponseDto mapToResponseDto(Subcategory subcategory) {
        return SubcategoryResponseDto.builder()
                .id(subcategory.getId())
                .nombre(subcategory.getNombre())
                .category(subcategory.getCategory())
                .activo(subcategory.getActivo())
                .build();
    }

    @Transactional
    public SubcategoryResponseDto update(Long id, SubcategoryRequestDto dto) {
        Long negocioId = SecurityUtils.getNegocioId();

        Subcategory subcategory = subcategoryRepository
                .findByIdAndNegocioIdAndStoreId(id, negocioId, storeContext.storeId())
                .orElseThrow(() -> new ResourceNotFoundException("Subcategoría no encontrada"));

        if (subcategoryRepository.existsByNombreAndCategoryAndNegocioIdAndStoreIdAndIdNot(
                dto.getNombre(),
                dto.getCategory(),
                negocioId, storeContext.storeId(),
                id
        )) {
            throw new DuplicateResourceException("La subcategoría ya existe en esa categoría");
        }

        Map<String, Object> before = Map.of("nombre", subcategory.getNombre(), "category", subcategory.getCategory());

        subcategory.setNombre(dto.getNombre());
        subcategory.setCategory(dto.getCategory());

        Subcategory saved = subcategoryRepository.save(subcategory);

        auditEventService.recordSuccess(AuditEntityType.SUBCATEGORY, id, AuditAction.SUBCATEGORY_UPDATED,
                before, Map.of("nombre", saved.getNombre(), "category", saved.getCategory()));

        return mapToResponseDto(saved);
    }
}
