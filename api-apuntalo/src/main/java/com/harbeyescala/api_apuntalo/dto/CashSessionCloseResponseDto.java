package com.harbeyescala.api_apuntalo.dto;
import com.harbeyescala.api_apuntalo.entity.enums.*;
import lombok.*; import java.math.BigDecimal; import java.time.LocalDateTime;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CashSessionCloseResponseDto {
 private Long cashSessionId; private CashSessionStatus status; private Long cashRegisterId; private String cashRegisterName;
 private Long responsibleId; private String responsibleUsername; private LocalDateTime openedAt; private BigDecimal openingFloat;
 private Boolean reconciliationRequired; private Long closedById; private String closedByUsername; private LocalDateTime closedAt;
 private CashSessionCloseMode closeMode; private BigDecimal cashSales; private BigDecimal cardSales; private BigDecimal totalSales;
 private BigDecimal cashIn; private BigDecimal cashOut; private BigDecimal expectedCashAtClose; private BigDecimal countedCash;
 private BigDecimal difference; private Long pendingTicketCountAtClose; private BigDecimal pendingTicketAmountAtClose;
 private Boolean pendingTicketsAcknowledged;
}
