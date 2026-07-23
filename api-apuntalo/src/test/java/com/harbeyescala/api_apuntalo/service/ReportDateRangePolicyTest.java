package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportDateRangePolicyTest {

    private final ReportDateRangePolicy policy = new ReportDateRangePolicy(366);

    @Test
    void acceptsExactlyMaximumInclusiveDays() {
        assertThatCode(() -> policy.validate(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 1, 1)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsOneDayOverMaximum() {
        assertThatThrownBy(() -> policy.validate(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 1, 2)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(
                        ((BusinessRuleException) ex).getCode()).isEqualTo("DATE_RANGE_TOO_LARGE"));
    }

    @Test
    void rejectsInvertedAndMissingRanges() {
        assertThatThrownBy(() -> policy.validate(
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 1, 1)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(
                        ((BusinessRuleException) ex).getCode()).isEqualTo("INVALID_DATE_RANGE"));

        assertThatThrownBy(() -> policy.validate(null, LocalDate.of(2026, 1, 1)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new ReportDateRangePolicy(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
