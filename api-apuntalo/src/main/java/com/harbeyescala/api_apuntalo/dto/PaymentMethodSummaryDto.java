package com.harbeyescala.api_apuntalo.dto;

import java.math.BigDecimal;

public class PaymentMethodSummaryDto {

    private BigDecimal cashTotal;
    private BigDecimal cardTotal;
    private BigDecimal totalSales;

    public PaymentMethodSummaryDto(BigDecimal cashTotal, BigDecimal cardTotal) {
        this.cashTotal = cashTotal;
        this.cardTotal = cardTotal;
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

        public BigDecimal getTotalSales() {
    return totalSales;
    }

    public void setTotalSales(BigDecimal totalSales) {
        this.totalSales = totalSales;
    }

    public PaymentMethodSummaryDto(BigDecimal cashTotal, BigDecimal cardTotal, BigDecimal totalSales) {
        this.cashTotal = cashTotal;
        this.cardTotal = cardTotal;
        this.totalSales = totalSales;
    }
}