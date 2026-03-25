package com.harbeyescala.api_apuntalo.dto;

import java.math.BigDecimal;

public class AverageTicketSummaryDto {

    private Long ticketCount;
    private BigDecimal totalSales;
    private BigDecimal averageTicket;

    public AverageTicketSummaryDto(Long ticketCount, BigDecimal totalSales, BigDecimal averageTicket) {
        this.ticketCount = ticketCount;
        this.totalSales = totalSales;
        this.averageTicket = averageTicket;
    }

    public Long getTicketCount() {
        return ticketCount;
    }

    public void setTicketCount(Long ticketCount) {
        this.ticketCount = ticketCount;
    }

    public BigDecimal getTotalSales() {
        return totalSales;
    }

    public void setTotalSales(BigDecimal totalSales) {
        this.totalSales = totalSales;
    }

    public BigDecimal getAverageTicket() {
        return averageTicket;
    }

    public void setAverageTicket(BigDecimal averageTicket) {
        this.averageTicket = averageTicket;
    }
}