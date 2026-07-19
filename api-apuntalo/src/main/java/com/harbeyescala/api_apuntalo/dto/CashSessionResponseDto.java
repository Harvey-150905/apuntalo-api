package com.harbeyescala.api_apuntalo.dto;

import com.harbeyescala.api_apuntalo.entity.enums.CashSessionStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashSessionResponseDto {
    private Long id;
    private CashSessionStatus status;
    private BigDecimal openingFloat;
    private Boolean reconciliationRequired;
    private LocalDateTime openedAt;
    private Long cashRegisterId;
    private String cashRegisterName;
    private Long responsibleUserId;
    private String responsibleUsername;
}
