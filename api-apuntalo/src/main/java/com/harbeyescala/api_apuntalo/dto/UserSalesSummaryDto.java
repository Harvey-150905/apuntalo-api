package com.harbeyescala.api_apuntalo.dto;

import java.math.BigDecimal;

public class UserSalesSummaryDto {

    private Long userId;
    private String username;
    private BigDecimal totalSales;

    public UserSalesSummaryDto(Long userId, String username, BigDecimal totalSales) {
        this.userId = userId;
        this.username = username;
        this.totalSales = totalSales;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public BigDecimal getTotalSales() {
        return totalSales;
    }

    public void setTotalSales(BigDecimal totalSales) {
        this.totalSales = totalSales;
    }
}