package com.harbeyescala.api_apuntalo.controller;

import com.harbeyescala.api_apuntalo.dto.NegocioResponseDto;
import com.harbeyescala.api_apuntalo.service.NegocioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public/negocios")
public class PublicNegocioController {

    private final NegocioService negocioService;

    public PublicNegocioController(NegocioService negocioService) {
        this.negocioService = negocioService;
    }

    @GetMapping
    public ResponseEntity<List<NegocioResponseDto>> findAll() {
        return ResponseEntity.ok(negocioService.findActivos());
    }
}