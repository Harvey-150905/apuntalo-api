package com.harbeyescala.api_apuntalo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductUpdateDto {

    @NotBlank(message = "El nombre es obligatorio")
    private String name;

    @NotNull(message = "La subcategoría es obligatoria")
    private Long subcategoryId;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.0", inclusive = false, message = "El precio debe ser mayor que 0")
    private BigDecimal price;

    private String description;

    // private String imageUrl;

    // private String imagePublicId;

    private Boolean activo;
}