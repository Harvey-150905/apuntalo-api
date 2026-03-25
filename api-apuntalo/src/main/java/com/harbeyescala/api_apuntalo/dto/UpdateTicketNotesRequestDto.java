package com.harbeyescala.api_apuntalo.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateTicketNotesRequestDto {

    @Size(max = 500, message = "Las notas no pueden superar los 500 caracteres")
    private String notes;
}