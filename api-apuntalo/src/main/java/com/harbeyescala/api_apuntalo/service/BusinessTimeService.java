package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.entity.Store;
import com.harbeyescala.api_apuntalo.exception.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Política temporal compatible con las columnas históricas
 * {@code timestamp without time zone}.
 *
 * <p>Los valores persistidos continúan representándose en la zona de
 * almacenamiento configurada. Los límites de negocio se construyen en la
 * timezone de la Store activa y se convierten a dicha representación antes
 * de consultar.</p>
 */
@Component
public class BusinessTimeService {

    private final ActiveStoreContext activeStoreContext;
    private final Clock clock;
    private final ZoneId storageZone;

    public BusinessTimeService(
            ActiveStoreContext activeStoreContext,
            Clock clock,
            @Value("${app.business-zone:Europe/Madrid}") String storageZone
    ) {
        this.activeStoreContext = activeStoreContext;
        this.clock = clock;
        this.storageZone = requireZone(storageZone, "app.business-zone");
    }

    public StoreDateRange range(LocalDate from, LocalDate toInclusive) {
        ZoneId storeZone = activeStoreZone();
        Instant fromInstant = from.atStartOfDay(storeZone).toInstant();
        Instant toExclusiveInstant = toInclusive.plusDays(1).atStartOfDay(storeZone).toInstant();
        return new StoreDateRange(
                LocalDateTime.ofInstant(fromInstant, storageZone),
                LocalDateTime.ofInstant(toExclusiveInstant, storageZone),
                storeZone
        );
    }

    public LocalDate businessDate(LocalDateTime storedDateTime, ZoneId storeZone) {
        return storedDateTime.atZone(storageZone)
                .withZoneSameInstant(storeZone)
                .toLocalDate();
    }

    public LocalDateTime nowForStorage() {
        return LocalDateTime.ofInstant(clock.instant(), storageZone);
    }

    public ZoneId activeStoreZone() {
        Store store = activeStoreContext.requireStore();
        return requireZone(store.getTimezone(), "Store.timezone");
    }

    ZoneId storageZone() {
        return storageZone;
    }

    private static ZoneId requireZone(String value, String source) {
        try {
            return ZoneId.of(value);
        } catch (DateTimeException | NullPointerException ex) {
            throw new BusinessRuleException(
                    "INVALID_STORE_TIMEZONE",
                    source + " contiene una zona horaria inválida"
            );
        }
    }

    public record StoreDateRange(
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive,
            ZoneId storeZone
    ) {
    }
}
