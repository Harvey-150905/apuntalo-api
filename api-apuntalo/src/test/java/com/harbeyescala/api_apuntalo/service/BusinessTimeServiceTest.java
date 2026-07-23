package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.entity.Store;
import com.harbeyescala.api_apuntalo.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessTimeServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-23T12:34:56Z"), ZoneId.of("Asia/Tokyo"));

    @Test
    void sameStoredInstantBelongsToDifferentBusinessDates() {
        LocalDateTime storedUtc = LocalDateTime.of(2026, 7, 23, 2, 0);

        BusinessTimeService madrid = service("Europe/Madrid");
        BusinessTimeService lima = service("America/Lima");

        assertThat(madrid.businessDate(storedUtc, madrid.activeStoreZone()))
                .isEqualTo(LocalDate.of(2026, 7, 23));
        assertThat(lima.businessDate(storedUtc, lima.activeStoreZone()))
                .isEqualTo(LocalDate.of(2026, 7, 22));
    }

    @Test
    void rangeUsesInclusiveLocalStartAndExclusiveNextLocalStart() {
        BusinessTimeService service = service("America/Lima");

        BusinessTimeService.StoreDateRange range =
                service.range(LocalDate.of(2026, 7, 23), LocalDate.of(2026, 7, 23));

        assertThat(range.fromInclusive()).isEqualTo(LocalDateTime.of(2026, 7, 23, 5, 0));
        assertThat(range.toExclusive()).isEqualTo(LocalDateTime.of(2026, 7, 24, 5, 0));
        assertThat(range.fromInclusive().minusSeconds(1).isBefore(range.fromInclusive())).isTrue();
        assertThat(range.toExclusive().minusSeconds(1)).isBetween(
                range.fromInclusive(), range.toExclusive());
        assertThat(range.toExclusive().isBefore(range.toExclusive())).isFalse();
    }

    @Test
    void madridSpringDstDayHasTwentyThreeHours() {
        BusinessTimeService.StoreDateRange range = service("Europe/Madrid")
                .range(LocalDate.of(2026, 3, 29), LocalDate.of(2026, 3, 29));

        assertThat(Duration.between(
                range.fromInclusive().atZone(ZoneId.of("UTC")).toInstant(),
                range.toExclusive().atZone(ZoneId.of("UTC")).toInstant()))
                .isEqualTo(Duration.ofHours(23));
    }

    @Test
    void madridAutumnDstDayHasTwentyFiveHours() {
        BusinessTimeService.StoreDateRange range = service("Europe/Madrid")
                .range(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 25));

        assertThat(Duration.between(
                range.fromInclusive().atZone(ZoneId.of("UTC")).toInstant(),
                range.toExclusive().atZone(ZoneId.of("UTC")).toInstant()))
                .isEqualTo(Duration.ofHours(25));
    }

    @Test
    void limaDaysRemainTwentyFourHoursAcrossEuropeanDstDates() {
        BusinessTimeService service = service("America/Lima");

        for (LocalDate date : new LocalDate[]{
                LocalDate.of(2026, 3, 29),
                LocalDate.of(2026, 10, 25)
        }) {
            BusinessTimeService.StoreDateRange range = service.range(date, date);
            assertThat(Duration.between(
                    range.fromInclusive().atZone(ZoneId.of("UTC")).toInstant(),
                    range.toExclusive().atZone(ZoneId.of("UTC")).toInstant()))
                    .isEqualTo(Duration.ofHours(24));
        }
    }

    @Test
    void writesUseClockInstantNotItsZoneAsBusinessZone() {
        assertThat(service("America/Lima").nowForStorage())
                .isEqualTo(LocalDateTime.of(2026, 7, 23, 12, 34, 56));
    }

    @Test
    void invalidStoreTimezoneFailsWithoutFallback() {
        assertThatThrownBy(() -> service("Invalid/Timezone").activeStoreZone())
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Store.timezone");
    }

    private BusinessTimeService service(String storeTimezone) {
        ActiveStoreContext context = mock(ActiveStoreContext.class);
        Store store = Store.builder().timezone(storeTimezone).build();
        when(context.requireStore()).thenReturn(store);
        return new BusinessTimeService(context, FIXED_CLOCK, "UTC");
    }
}
