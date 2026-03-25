package com.harbeyescala.api_apuntalo.dto;

import java.math.BigDecimal;

public class ProductSalesSummaryDto {

    private Long productId;
    private String productName;
    private Long totalQuantity;
    private BigDecimal totalSales;

    public ProductSalesSummaryDto(Long productId, String productName, Long totalQuantity, BigDecimal totalSales) {
        this.productId = productId;
        this.productName = productName;
        this.totalQuantity = totalQuantity;
        this.totalSales = totalSales;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Long getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(Long totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public BigDecimal getTotalSales() {
        return totalSales;
    }

    public void setTotalSales(BigDecimal totalSales) {
        this.totalSales = totalSales;
    }
}