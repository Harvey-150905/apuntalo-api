package com.harbeyescala.api_apuntalo.controller;

import com.harbeyescala.api_apuntalo.dto.NegocioRequestDto;
import com.harbeyescala.api_apuntalo.dto.NegocioResponseDto;
import com.harbeyescala.api_apuntalo.service.NegocioService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/negocios")
public class NegocioController {

    private final NegocioService negocioService;

    public NegocioController(NegocioService negocioService) {
        this.negocioService = negocioService;
    }

    @PostMapping
    public ResponseEntity<NegocioResponseDto> create(@Valid @RequestBody NegocioRequestDto dto) {
        NegocioResponseDto savedNegocio = negocioService.save(dto);
        return ResponseEntity.ok(savedNegocio);
    }

    @GetMapping
    public ResponseEntity<List<NegocioResponseDto>> findAll() {
        return ResponseEntity.ok(negocioService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<NegocioResponseDto> findById(@PathVariable Long id) {
        return negocioService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable Long id) {
        negocioService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
    @PutMapping("/{id}")
    public ResponseEntity<NegocioResponseDto> update(@PathVariable Long id, @Valid @RequestBody NegocioRequestDto dto) {
        NegocioResponseDto updatedNegocio = negocioService.update(id, dto);
        return ResponseEntity.ok(updatedNegocio);
    }
}