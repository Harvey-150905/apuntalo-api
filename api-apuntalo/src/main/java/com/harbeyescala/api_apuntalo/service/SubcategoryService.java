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

    public SubcategoryService(SubcategoryRepository subcategoryRepository) {
        this.subcategoryRepository = subcategoryRepository;
    }

    public SubcategoryResponseDto save(SubcategoryRequestDto dto) {
        Long negocioId = SecurityUtils.getNegocioId();

        if (subcategoryRepository.existsByNombreAndCategoryAndNegocioId(
                dto.getNombre(),
                dto.getCategory(),
                negocioId
        )) {
            throw new DuplicateResourceException("La subcategoría ya existe en esa categoría");
        }

        Subcategory subcategory = Subcategory.builder()
                .nombre(dto.getNombre())
                .category(dto.getCategory())
                .negocio(new Negocio(negocioId)) // 👈 IMPORTANTE
                .build();

        return mapToResponseDto(subcategoryRepository.save(subcategory));
    }

    public List<SubcategoryResponseDto> findAll() {
        Long negocioId = SecurityUtils.getNegocioId();

        return subcategoryRepository.findByNegocioId(negocioId)
                .stream()
                .map(this::mapToResponseDto)
                .toList();
    }

    public Optional<SubcategoryResponseDto> findById(Long id) {
        Long negocioId = SecurityUtils.getNegocioId();

        return subcategoryRepository.findByIdAndNegocioId(id, negocioId)
                .map(this::mapToResponseDto);
    }

    public void deleteById(Long id) {
        Long negocioId = SecurityUtils.getNegocioId();

        Subcategory subcategory = subcategoryRepository
                .findByIdAndNegocioId(id, negocioId)
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
                .findByIdAndNegocioId(id, negocioId)
                .orElseThrow(() -> new ResourceNotFoundException("Subcategoría no encontrada"));

        if (subcategoryRepository.existsByNombreAndCategoryAndNegocioIdAndIdNot(
                dto.getNombre(),
                dto.getCategory(),
                negocioId,
                id
        )) {
            throw new DuplicateResourceException("La subcategoría ya existe en esa categoría");
        }

        subcategory.setNombre(dto.getNombre());
        subcategory.setCategory(dto.getCategory());

        return mapToResponseDto(subcategoryRepository.save(subcategory));
    }
}