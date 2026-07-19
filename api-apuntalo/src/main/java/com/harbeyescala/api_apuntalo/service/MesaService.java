package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.dto.MesaRequestDto;
import com.harbeyescala.api_apuntalo.dto.MesaResponseDto;
import com.harbeyescala.api_apuntalo.entity.Mesa;
import com.harbeyescala.api_apuntalo.entity.Negocio;
import com.harbeyescala.api_apuntalo.entity.enums.MesaStatus;
import com.harbeyescala.api_apuntalo.exception.DuplicateResourceException;
import com.harbeyescala.api_apuntalo.exception.ResourceNotFoundException;
import com.harbeyescala.api_apuntalo.repository.MesaRepository;
import com.harbeyescala.api_apuntalo.repository.NegocioRepository;
import com.harbeyescala.api_apuntalo.security.SecurityUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MesaService {

    private final MesaRepository mesaRepository;
    private final NegocioRepository negocioRepository;
    private final ActiveStoreContext storeContext;

    public MesaService(MesaRepository mesaRepository, NegocioRepository negocioRepository, ActiveStoreContext storeContext) {
        this.mesaRepository = mesaRepository;
        this.negocioRepository = negocioRepository;
        this.storeContext = storeContext;
    }

    public MesaResponseDto create(MesaRequestDto dto) {
        Long negocioId = SecurityUtils.getNegocioId();

        Long storeId=storeContext.storeId();
        if (mesaRepository.existsByNumeroAndNegocioIdAndStoreId(dto.getNumero(), negocioId,storeId)) {
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

        return toResponse(mesaRepository.save(mesa));
    }

    public List<MesaResponseDto> findAll() {
        Long negocioId = SecurityUtils.getNegocioId();
        return mesaRepository.findByNegocioIdAndStoreId(negocioId,storeContext.storeId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<MesaResponseDto> findAllActivas() {
        Long negocioId = SecurityUtils.getNegocioId();
        return mesaRepository.findByNegocioIdAndStoreIdAndActivaTrue(negocioId,storeContext.storeId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public MesaResponseDto findById(Long id) {
        Long negocioId = SecurityUtils.getNegocioId();

        Mesa mesa = mesaRepository.findByIdAndNegocioIdAndStoreId(id, negocioId,storeContext.storeId())
                .orElseThrow(() -> new ResourceNotFoundException("Mesa no encontrada"));

        return toResponse(mesa);
    }

    public MesaResponseDto update(Long id, MesaRequestDto dto) {
        Long negocioId = SecurityUtils.getNegocioId();

        Mesa mesa = mesaRepository.findByIdAndNegocioIdAndStoreId(id, negocioId,storeContext.storeId())
                .orElseThrow(() -> new ResourceNotFoundException("Mesa no encontrada"));

        if (mesaRepository.existsByNumeroAndNegocioIdAndStoreIdAndIdNot(dto.getNumero(), negocioId,storeContext.storeId(), id)) {
            throw new DuplicateResourceException("Ya existe otra mesa con ese número");
        }

        mesa.setNumero(dto.getNumero());

        if (dto.getActiva() != null) {
            mesa.setActiva(dto.getActiva());
        }

        return toResponse(mesaRepository.save(mesa));
    }

    public void delete(Long id) {
        Long negocioId = SecurityUtils.getNegocioId();

        Mesa mesa = mesaRepository.findByIdAndNegocioIdAndStoreId(id, negocioId,storeContext.storeId())
                .orElseThrow(() -> new ResourceNotFoundException("Mesa no encontrada"));

        mesa.setActiva(false);
        mesaRepository.save(mesa);
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
