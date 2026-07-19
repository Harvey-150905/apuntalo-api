package com.harbeyescala.api_apuntalo.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface CashSessionSummaryProjection {
    String getStatus();
    Long getSessionId(); Long getCashRegisterId(); String getCashRegisterName();
    Long getResponsibleUserId(); String getResponsibleUsername(); LocalDateTime getOpenedAt();
    BigDecimal getOpeningFloat(); Boolean getReconciliationRequired();
    BigDecimal getCashSales(); BigDecimal getCardSales(); BigDecimal getTotalSales(); BigDecimal getExpectedCash();
    BigDecimal getCashIn(); BigDecimal getCashOut(); LocalDateTime getClosedAt(); Long getClosedById();
    String getClosedByUsername(); String getCloseMode(); BigDecimal getExpectedCashAtClose();
    BigDecimal getCountedCash(); BigDecimal getDifference(); Long getPendingTicketCountAtClose();
    BigDecimal getPendingTicketAmountAtClose(); Boolean getPendingTicketsAcknowledged();
    Long getTicketCount(); Long getCashPaymentCount(); Long getCardPaymentCount();
    Long getTicketsOpenedCount(); Long getOpenOriginTicketsCount(); BigDecimal getOpenOriginTicketsAmount();
    Long getTicketsOriginatedHerePaidHereCount(); Long getTicketsOriginatedHerePaidElsewhereCount();
    Long getTicketsFromOtherSessionsPaidHereCount();
}
