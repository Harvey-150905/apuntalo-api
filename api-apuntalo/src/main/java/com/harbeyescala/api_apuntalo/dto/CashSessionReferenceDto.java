package com.harbeyescala.api_apuntalo.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashSessionReferenceDto {
    private Long id;
    private Long cashRegisterId;
    private String cashRegisterName;
    private Long responsibleUserId;
    private String responsibleUsername;
    private LocalDateTime openedAt;
}
