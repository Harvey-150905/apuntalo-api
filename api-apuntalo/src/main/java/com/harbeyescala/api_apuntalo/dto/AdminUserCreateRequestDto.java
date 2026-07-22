package com.harbeyescala.api_apuntalo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.harbeyescala.api_apuntalo.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Alta administrativa de un usuario (Fase 9, F9.4). Incluye las Stores
 * autorizables iniciales y la Store predeterminada, que debe pertenecer al
 * conjunto. La contraseña inicial exige un mínimo de 8 caracteres.
 */
@Getter
@Setter
public class AdminUserCreateRequestDto {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotBlank(message = "El username es obligatorio")
    @Size(max = 100, message = "El username no puede superar los 100 caracteres")
    private String username;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @NotNull(message = "El rol es obligatorio")
    private Role role;

    @Positive(message = "El negocioId debe ser un id válido")
    private Long negocioId;

    @NotEmpty(message = "Debe indicar al menos una tienda")
    private List<Long> storeIds;

    @NotNull(message = "La tienda predeterminada es obligatoria")
    private Long defaultStoreId;
}
