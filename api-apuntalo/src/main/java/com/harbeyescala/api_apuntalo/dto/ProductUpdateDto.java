package com.harbeyescala.api_apuntalo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductUpdateDto {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 255, message = "El nombre no puede superar los 255 caracteres")
    private String name;

    @NotNull(message = "La subcategoría es obligatoria")
    @Positive(message = "La subcategoría debe ser un id válido")
    private Long subcategoryId;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.0", inclusive = false, message = "El precio debe ser mayor que 0")
    @Digits(integer = 8, fraction = 2, message = "El precio debe tener como máximo 8 enteros y 2 decimales")
    private BigDecimal price;

    @Size(max = 1000, message = "La descripción no puede superar los 1000 caracteres")
    private String description;

    // private String imageUrl;

    // private String imagePublicId;

    private Boolean activo;
}
