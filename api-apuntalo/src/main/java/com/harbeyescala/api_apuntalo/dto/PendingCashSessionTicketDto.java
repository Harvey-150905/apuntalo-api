package com.harbeyescala.api_apuntalo.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PendingCashSessionTicketDto {
    private Long ticketId;
    private Long commercialNumber;
    private Long mesaId;
    private Integer mesaNumero;
    private LocalDateTime createdAt;
    private Long createdById;
    private String createdByUsername;
    private BigDecimal total;
    private Long activeLineCount;
}
