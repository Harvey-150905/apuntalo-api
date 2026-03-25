package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.dto.NegocioRequestDto;
import com.harbeyescala.api_apuntalo.dto.NegocioResponseDto;
import com.harbeyescala.api_apuntalo.entity.Negocio;
import com.harbeyescala.api_apuntalo.repository.NegocioRepository;
import com.harbeyescala.api_apuntalo.security.SecurityUtils;

import org.springframework.stereotype.Service;
import com.harbeyescala.api_apuntalo.exception.ResourceNotFoundException;

import java.util.List;
import java.util.Optional;

@Service
public class NegocioService {

    private final NegocioRepository negocioRepository;

    public NegocioService(NegocioRepository negocioRepository) {
        this.negocioRepository = negocioRepository;
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

    public void deleteById(Long id) {
        Negocio negocio = negocioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado"));

        negocioRepository.delete(negocio);
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