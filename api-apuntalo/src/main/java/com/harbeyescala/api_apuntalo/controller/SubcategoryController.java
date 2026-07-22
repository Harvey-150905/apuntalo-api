package com.harbeyescala.api_apuntalo.controller;

import com.harbeyescala.api_apuntalo.dto.ActiveStatusRequestDto;
import com.harbeyescala.api_apuntalo.dto.SubcategoryRequestDto;
import com.harbeyescala.api_apuntalo.dto.SubcategoryResponseDto;
import com.harbeyescala.api_apuntalo.service.SubcategoryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subcategories")
public class SubcategoryController {

    private final SubcategoryService subcategoryService;

    public SubcategoryController(SubcategoryService subcategoryService) {
        this.subcategoryService = subcategoryService;
    }

    @PostMapping
    public ResponseEntity<SubcategoryResponseDto> create(@Valid @RequestBody SubcategoryRequestDto dto) {
        SubcategoryResponseDto savedSubcategory = subcategoryService.save(dto);
        return ResponseEntity.ok(savedSubcategory);
    }

    @GetMapping
    public ResponseEntity<List<SubcategoryResponseDto>> findAll() {
        return ResponseEntity.ok(subcategoryService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubcategoryResponseDto> findById(@PathVariable Long id) {
        return subcategoryService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable Long id) {
        subcategoryService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
    @PutMapping("/{id}")
    public ResponseEntity<SubcategoryResponseDto> update(
            @PathVariable Long id,
            @Valid @RequestBody SubcategoryRequestDto dto
    ) {
        SubcategoryResponseDto updatedSubcategory = subcategoryService.update(id, dto);
        return ResponseEntity.ok(updatedSubcategory);
    }

    /**
     * Fase 9 (F9.7): activa/desactiva una subcategoría. Sustituye al borrado físico.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<SubcategoryResponseDto> setStatus(
            @PathVariable Long id,
            @Valid @RequestBody ActiveStatusRequestDto dto
    ) {
        return ResponseEntity.ok(subcategoryService.setActive(id, dto.getActive()));
    }
}