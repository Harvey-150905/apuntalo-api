package com.harbeyescala.api_apuntalo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TicketRequestDto {

    @NotNull(message = "La mesa es obligatoria")
    private Long mesaId;

    private String notes;
}