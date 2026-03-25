package com.harbeyescala.api_apuntalo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChangeTicketMesaRequestDto {

    @NotNull(message = "La mesa es obligatoria")
    private Long mesaId;
}