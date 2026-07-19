package com.harbeyescala.api_apuntalo.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.harbeyescala.api_apuntalo.entity.enums.CashSessionCloseMode;
import com.harbeyescala.api_apuntalo.entity.enums.CashSessionStatus;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CashSessionSummaryDto {
    private Long sessionId;
    private CashSessionStatus status;
    private Long cashRegisterId;
    private String cashRegisterName;
    private Long responsibleUserId;
    private String responsibleUsername;
    private LocalDateTime openedAt;
    private BigDecimal openingFloat;
    private Boolean reconciliationRequired;
    private BigDecimal cashSales;
    private BigDecimal cardSales;
    private BigDecimal totalSales;
    private BigDecimal expectedCash;
    private BigDecimal cashIn;
    private BigDecimal cashOut;
    private LocalDateTime closedAt;
    private Long closedById;
    private String closedByUsername;
    private CashSessionCloseMode closeMode;
    private BigDecimal expectedCashAtClose;
    private BigDecimal countedCash;
    private BigDecimal difference;
    private Long pendingTicketCountAtClose;
    private BigDecimal pendingTicketAmountAtClose;
    private Boolean pendingTicketsAcknowledged;
    private Long ticketCount;
    private Long cashPaymentCount;
    private Long cardPaymentCount;
    private Long ticketsOpenedCount;
    private Long openOriginTicketsCount;
    private BigDecimal openOriginTicketsAmount;
    private Long ticketsOriginatedHerePaidHereCount;
    private Long ticketsOriginatedHerePaidElsewhereCount;
    private Long ticketsFromOtherSessionsPaidHereCount;
}
