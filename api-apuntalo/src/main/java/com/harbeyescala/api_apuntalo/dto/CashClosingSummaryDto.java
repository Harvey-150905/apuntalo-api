package com.harbeyescala.api_apuntalo.dto;

import java.math.BigDecimal;

public class CashClosingSummaryDto {

    private BigDecimal totalSales;
    private BigDecimal cashTotal;
    private BigDecimal cardTotal;
    private Long paidTickets;
    private Long cancelledTickets;

    public CashClosingSummaryDto(BigDecimal totalSales, BigDecimal cashTotal, BigDecimal cardTotal,
                                 Long paidTickets, Long cancelledTickets) {
        this.totalSales = totalSales;
        this.cashTotal = cashTotal;
        this.cardTotal = cardTotal;
        this.paidTickets = paidTickets;
        this.cancelledTickets = cancelledTickets;
    }

    public BigDecimal getTotalSales() {
        return totalSales;
    }

    public void setTotalSales(BigDecimal totalSales) {
        this.totalSales = totalSales;
    }

    public BigDecimal getCashTotal() {
        return cashTotal;
    }

    public void setCashTotal(BigDecimal cashTotal) {
        this.cashTotal = cashTotal;
    }

    public BigDecimal getCardTotal() {
        return cardTotal;
    }

    public void setCardTotal(BigDecimal cardTotal) {
        this.cardTotal = cardTotal;
    }

    public Long getPaidTickets() {
        return paidTickets;
    }

    public void setPaidTickets(Long paidTickets) {
        this.paidTickets = paidTickets;
    }

    public Long getCancelledTickets() {
        return cancelledTickets;
    }

    public void setCancelledTickets(Long cancelledTickets) {
        this.cancelledTickets = cancelledTickets;
    }
}