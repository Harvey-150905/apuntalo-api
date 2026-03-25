package com.harbeyescala.api_apuntalo.controller;

import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harbeyescala.api_apuntalo.dto.ProductRequestDto;
import com.harbeyescala.api_apuntalo.dto.ProductResponseDto;
import com.harbeyescala.api_apuntalo.dto.ProductUpdateDto;
import com.harbeyescala.api_apuntalo.service.ProductService;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public ProductController(
            ProductService productService,
            ObjectMapper objectMapper,
            Validator validator
    ) {
        this.productService = productService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ProductResponseDto create(
            @RequestPart("product") String productJson,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) throws Exception {
        ProductRequestDto dto = objectMapper.readValue(productJson, ProductRequestDto.class);
        validate(dto);
        return productService.save(dto, image);
    }

    @GetMapping
    public List<ProductResponseDto> findAll() {
        return productService.findAll();
    }

    @GetMapping("/activos")
    public List<ProductResponseDto> findAllActivos() {
        return productService.findAllActivos();
    }

    @GetMapping("/{id}")
    public ProductResponseDto findById(@PathVariable Long id) {
        return productService.findById(id);
    }

    @PutMapping(value = "/{id}", consumes = "multipart/form-data")
    public ProductResponseDto update(
            @PathVariable Long id,
            @RequestPart("product") String productJson,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) throws Exception {
        ProductUpdateDto dto = objectMapper.readValue(productJson, ProductUpdateDto.class);
        validate(dto);
        return productService.update(id, dto, image);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }

    private <T> void validate(T dto) {
        Set<ConstraintViolation<T>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}