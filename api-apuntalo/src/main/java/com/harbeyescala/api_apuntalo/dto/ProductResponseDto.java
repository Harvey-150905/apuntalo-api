package com.harbeyescala.api_apuntalo.dto;

import com.harbeyescala.api_apuntalo.entity.Category;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ProductResponseDto {

    private Long id;
    private String name;
    private Category category;
    private Long subcategoryId;
    private String subcategoryNombre;
    private BigDecimal price;
    private String description;
    private String imageUrl;
    private String imagePublicId;
    private Boolean activo;
    private Long negocioId;
    private String negocioNombre;
}