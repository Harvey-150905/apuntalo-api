package com.harbeyescala.api_apuntalo.dto;

import com.harbeyescala.api_apuntalo.entity.enums.MesaStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MesaResponseDto {
    private Long id;
    private Integer numero;
    private MesaStatus status;
    private Boolean activa;
}