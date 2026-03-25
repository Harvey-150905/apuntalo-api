package com.harbeyescala.api_apuntalo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AddTicketLinesRequestDto {

    @Valid
    @NotEmpty(message = "Debes enviar al menos una línea")
    private List<TicketLineRequestDto> lines;
}