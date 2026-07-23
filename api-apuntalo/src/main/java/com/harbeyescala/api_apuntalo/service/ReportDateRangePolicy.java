package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.exception.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
public class ReportDateRangePolicy {

    private final long maxRangeDays;

    public ReportDateRangePolicy(
            @Value("${app.reports.max-range-days:366}") long maxRangeDays
    ) {
        if (maxRangeDays < 1) {
            throw new IllegalArgumentException("app.reports.max-range-days debe ser mayor que cero");
        }
        this.maxRangeDays = maxRangeDays;
    }

    public void validate(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessRuleException("INVALID_DATE_RANGE", "Debes enviar ambas fechas");
        }
        if (from.isAfter(to)) {
            throw new BusinessRuleException(
                    "INVALID_DATE_RANGE",
                    "La fecha 'from' no puede ser mayor que 'to'"
            );
        }
        if (LocalDate.MAX.equals(to)) {
            throw new BusinessRuleException(
                    "INVALID_DATE_RANGE",
                    "La fecha 'to' está fuera del rango permitido"
            );
        }

        long inclusiveDays = ChronoUnit.DAYS.between(from, to) + 1;
        if (inclusiveDays > maxRangeDays) {
            throw new BusinessRuleException(
                    "DATE_RANGE_TOO_LARGE",
                    "El rango no puede superar " + maxRangeDays + " días inclusivos"
            );
        }
    }

    public long maxRangeDays() {
        return maxRangeDays;
    }
}
