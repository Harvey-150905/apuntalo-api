package com.harbeyescala.api_apuntalo.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ChangeTicketMesaRequestDto {

    @NotNull(message = "La mesa es obligatoria")
    @Positive(message = "La mesa debe ser un id válido")
    private Long mesaId;
}