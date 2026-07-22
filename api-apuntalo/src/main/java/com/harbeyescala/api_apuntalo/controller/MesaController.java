package com.harbeyescala.api_apuntalo.controller;

import com.harbeyescala.api_apuntalo.dto.ActiveStatusRequestDto;
import com.harbeyescala.api_apuntalo.dto.MesaRequestDto;
import com.harbeyescala.api_apuntalo.dto.MesaResponseDto;
import com.harbeyescala.api_apuntalo.service.MesaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mesas")
public class MesaController {

    private final MesaService mesaService;

    public MesaController(MesaService mesaService) {
        this.mesaService = mesaService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MesaResponseDto create(@Valid @RequestBody MesaRequestDto dto) {
        return mesaService.create(dto);
    }

    @GetMapping
    public List<MesaResponseDto> findAll() {
        return mesaService.findAll();
    }

    @GetMapping("/activas")
    public List<MesaResponseDto> findAllActivas() {
        return mesaService.findAllActivas();
    }

    @GetMapping("/{id}")
    public MesaResponseDto findById(@PathVariable Long id) {
        return mesaService.findById(id);
    }

    @PutMapping("/{id}")
    public MesaResponseDto update(@PathVariable Long id, @Valid @RequestBody MesaRequestDto dto) {
        return mesaService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        mesaService.delete(id);
    }

    /**
     * Fase 9 (F9.6): activa/desactiva una mesa. Sustituye al borrado físico.
     */
    @PatchMapping("/{id}/status")
    public MesaResponseDto setStatus(@PathVariable Long id, @Valid @RequestBody ActiveStatusRequestDto dto) {
        return mesaService.setActive(id, dto.getActive());
    }
}