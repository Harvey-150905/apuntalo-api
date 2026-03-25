package com.harbeyescala.api_apuntalo.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class NegocioResponseDto {

    private Long id;
    private String nombre;
}