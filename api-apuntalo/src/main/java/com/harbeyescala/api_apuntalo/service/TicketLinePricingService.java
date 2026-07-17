package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.exception.BusinessRuleException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;
import java.util.TreeSet;

/**
 * Cálculo de descuentos por línea (Fase 5.1). Los porcentajes permitidos
 * son un catálogo cerrado (no cualquier valor entre 0 y 100) porque
 * corresponden a promociones comerciales predefinidas del negocio.
 */
@Service
public class TicketLinePricingService {

    private static final Set<Integer> ALLOWED_DISCOUNT_PERCENTAGES =
            Set.of(0, 5, 10, 15, 20, 30, 35, 40, 45, 50);

    public record LineDiscountCalculation(
            BigDecimal subtotalBeforeDiscount,
            BigDecimal discountAmount,
            BigDecimal subtotal
    ) {
    }

    public void validatePercentage(Integer discountPercentage) {
        if (discountPercentage == null || !ALLOWED_DISCOUNT_PERCENTAGES.contains(discountPercentage)) {
            throw new BusinessRuleException(
                    "INVALID_DISCOUNT_PERCENTAGE",
                    "El porcentaje de descuento debe ser uno de: " + new TreeSet<>(ALLOWED_DISCOUNT_PERCENTAGES)
            );
        }
    }

    /**
     * before = unitPrice * qty; discount = before * pct / 100 (HALF_UP,
     * escala 2); subtotal = before - discount. El redondeo se aplica en
     * cada paso para que el importe persistido coincida siempre con el
     * check constraint {@code subtotal = subtotal_before_discount - discount_amount}.
     */
    public LineDiscountCalculation calculate(BigDecimal unitPrice, Integer quantity, Integer discountPercentage) {
        validatePercentage(discountPercentage);

        BigDecimal before = unitPrice
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal discount = before
                .multiply(BigDecimal.valueOf(discountPercentage))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal subtotal = before.subtract(discount).setScale(2, RoundingMode.HALF_UP);

        return new LineDiscountCalculation(before, discount, subtotal);
    }

    /**
     * El 0% se considera "sin descuento": limpia quién/cuándo lo aplicó
     * (constraint {@code ck_ticket_lines_zero_discount_actor}).
     */
    public boolean isNoDiscount(Integer discountPercentage) {
        return discountPercentage != null && discountPercentage == 0;
    }
}
