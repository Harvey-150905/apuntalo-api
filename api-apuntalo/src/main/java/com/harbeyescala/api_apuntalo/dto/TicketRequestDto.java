package com.harbeyescala.api_apuntalo.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TicketRequestDto {

    @NotNull(message = "La mesa es obligatoria")
    @Positive(message = "La mesa debe ser un id válido")
    private Long mesaId;

    @Size(max = 500, message = "Las notas no pueden superar los 500 caracteres")
    private String notes;
}