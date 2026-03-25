package com.harbeyescala.api_apuntalo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class DailySalesSummaryDto {

    private LocalDate date;
    private Long ticketCount;
    private BigDecimal totalSales;

    public DailySalesSummaryDto(LocalDate date, Long ticketCount, BigDecimal totalSales) {
        this.date = date;
        this.ticketCount = ticketCount;
        this.totalSales = totalSales;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
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
}