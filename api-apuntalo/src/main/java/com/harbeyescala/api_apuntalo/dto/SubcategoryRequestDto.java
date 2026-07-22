package com.harbeyescala.api_apuntalo.dto;

import com.harbeyescala.api_apuntalo.entity.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubcategoryRequestDto {

    @NotBlank(message = "El nombre de la subcategoría es obligatorio")
    private String nombre;

    @NotNull(message = "La categoría es obligatoria")
    private Category category;

    private Boolean activo;
}