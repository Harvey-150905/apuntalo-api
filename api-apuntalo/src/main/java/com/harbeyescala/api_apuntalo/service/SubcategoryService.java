package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.dto.SubcategoryRequestDto;
import com.harbeyescala.api_apuntalo.dto.SubcategoryResponseDto;
import com.harbeyescala.api_apuntalo.entity.Negocio;
import com.harbeyescala.api_apuntalo.entity.Subcategory;
import com.harbeyescala.api_apuntalo.repository.SubcategoryRepository;
import com.harbeyescala.api_apuntalo.security.SecurityUtils;

import org.springframework.stereotype.Service;
import com.harbeyescala.api_apuntalo.exception.DuplicateResourceException;
import com.harbeyescala.api_apuntalo.exception.ResourceNotFoundException;

import java.util.List;
import java.util.Optional;

@Service
public class SubcategoryService {

    private final SubcategoryRepository subcategoryRepository;
    private final ActiveStoreContext storeContext;

    public SubcategoryService(SubcategoryRepository subcategoryRepository,ActiveStoreContext storeContext) {
        this.subcategoryRepository = subcategoryRepository;
        this.storeContext=storeContext;
    }

    public SubcategoryResponseDto save(SubcategoryRequestDto dto) {
        Long negocioId = SecurityUtils.getNegocioId();

        if (subcategoryRepository.existsByNombreAndCategoryAndNegocioIdAndStoreId(
                dto.getNombre(),
                dto.getCategory(),
                negocioId,storeContext.storeId()
        )) {
            throw new DuplicateResourceException("La subcategoría ya existe en esa categoría");
        }

        Subcategory subcategory = Subcategory.builder()
                .nombre(dto.getNombre())
                .category(dto.getCategory())
                .negocio(new Negocio(negocioId)) // 👈 IMPORTANTE
                .store(storeContext.requireStore())
                .build();

        return mapToResponseDto(subcategoryRepository.save(subcategory));
    }

    public List<SubcategoryResponseDto> findAll() {
        Long negocioId = SecurityUtils.getNegocioId();

        return subcategoryRepository.findByNegocioIdAndStoreId(negocioId,storeContext.storeId())
                .stream()
                .map(this::mapToResponseDto)
                .toList();
    }

    public Optional<SubcategoryResponseDto> findById(Long id) {
        Long negocioId = SecurityUtils.getNegocioId();

        return subcategoryRepository.findByIdAndNegocioIdAndStoreId(id, negocioId,storeContext.storeId())
                .map(this::mapToResponseDto);
    }

    public void deleteById(Long id) {
        Long negocioId = SecurityUtils.getNegocioId();

        Subcategory subcategory = subcategoryRepository
                .findByIdAndNegocioIdAndStoreId(id, negocioId,storeContext.storeId())
                .orElseThrow(() -> new ResourceNotFoundException("Subcategoría no encontrada"));

        subcategoryRepository.delete(subcategory);
    }

    private SubcategoryResponseDto mapToResponseDto(Subcategory subcategory) {
        return SubcategoryResponseDto.builder()
                .id(subcategory.getId())
                .nombre(subcategory.getNombre())
                .category(subcategory.getCategory())
                .build();
    }
    public SubcategoryResponseDto update(Long id, SubcategoryRequestDto dto) {
        Long negocioId = SecurityUtils.getNegocioId();

        Subcategory subcategory = subcategoryRepository
                .findByIdAndNegocioIdAndStoreId(id, negocioId,storeContext.storeId())
                .orElseThrow(() -> new ResourceNotFoundException("Subcategoría no encontrada"));

        if (subcategoryRepository.existsByNombreAndCategoryAndNegocioIdAndStoreIdAndIdNot(
                dto.getNombre(),
                dto.getCategory(),
                negocioId,storeContext.storeId(),
                id
        )) {
            throw new DuplicateResourceException("La subcategoría ya existe en esa categoría");
        }

        subcategory.setNombre(dto.getNombre());
        subcategory.setCategory(dto.getCategory());

        return mapToResponseDto(subcategoryRepository.save(subcategory));
    }
}
