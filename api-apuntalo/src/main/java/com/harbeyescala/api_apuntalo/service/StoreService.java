package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.dto.StoreResponseDto;
import com.harbeyescala.api_apuntalo.entity.Store;
import com.harbeyescala.api_apuntalo.exception.BadRequestException;
import com.harbeyescala.api_apuntalo.exception.ResourceNotFoundException;
import com.harbeyescala.api_apuntalo.repository.StoreRepository;
import com.harbeyescala.api_apuntalo.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class StoreService {
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_CODE_LENGTH = 50;
    private static final int MAX_TIMEZONE_LENGTH = 64;
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z0-9_-]+");
    private static final Pattern COUNTRY_CODE = Pattern.compile("[A-Z]{2}");

    private final StoreRepository repository;
    private final CurrentUser currentUser;

    public StoreService(StoreRepository repository, CurrentUser currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public StoreResponseDto findById(Long id) {
        return toResponse(findTenantStore(id));
    }

    @Transactional(readOnly = true)
    public StoreResponseDto findPrimary() {
        return repository.findByNegocioIdAndPrimaryStoreTrue(currentUser.getTenantId())
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Tienda Principal no encontrada"));
    }

    @Transactional(readOnly = true)
    public List<StoreResponseDto> findAll() {
        return repository.findAllByNegocioIdOrderByNameAscIdAsc(currentUser.getTenantId())
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<StoreResponseDto> findActive() {
        return repository.findByNegocioIdAndActiveTrueOrderByNameAscIdAsc(currentUser.getTenantId())
                .stream().map(this::toResponse).toList();
    }

    public String cleanName(String rawName) {
        String name = requireTrimmed(rawName, "STORE_NAME_REQUIRED", "El nombre de la tienda es obligatorio");
        if (name.length() > MAX_NAME_LENGTH) {
            throw new BadRequestException("STORE_NAME_TOO_LONG", "El nombre no puede superar 100 caracteres");
        }
        return name;
    }

    public String normalizeName(String rawName) {
        return cleanName(rawName).toLowerCase(Locale.ROOT);
    }

    public String normalizeCode(String rawCode) {
        String code = requireTrimmed(rawCode, "STORE_CODE_REQUIRED", "El código de la tienda es obligatorio")
                .toUpperCase(Locale.ROOT);
        if (code.length() > MAX_CODE_LENGTH) {
            throw new BadRequestException("STORE_CODE_TOO_LONG", "El código no puede superar 50 caracteres");
        }
        if (!SAFE_CODE.matcher(code).matches()) {
            throw new BadRequestException("INVALID_STORE_CODE",
                    "El código solo puede contener letras, números, guion y guion bajo");
        }
        return code;
    }

    public String normalizeTimezone(String rawTimezone) {
        String timezone = requireTrimmed(rawTimezone, "STORE_TIMEZONE_REQUIRED",
                "La zona horaria de la tienda es obligatoria");
        if (timezone.length() > MAX_TIMEZONE_LENGTH) {
            throw new BadRequestException("STORE_TIMEZONE_TOO_LONG", "La zona horaria no puede superar 64 caracteres");
        }
        try {
            return ZoneId.of(timezone).getId();
        } catch (DateTimeException ex) {
            throw new BadRequestException("INVALID_STORE_TIMEZONE", "La zona horaria no es válida");
        }
    }

    public String normalizeCountryCode(String rawCountryCode) {
        String countryCode = requireTrimmed(rawCountryCode, "STORE_COUNTRY_CODE_REQUIRED",
                "El código de país es obligatorio").toUpperCase(Locale.ROOT);
        if (!COUNTRY_CODE.matcher(countryCode).matches()) {
            throw new BadRequestException("INVALID_STORE_COUNTRY_CODE",
                    "El código de país debe contener exactamente dos letras");
        }
        return countryCode;
    }

    private Store findTenantStore(Long id) {
        return repository.findByIdAndNegocioId(id, currentUser.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tienda no encontrada"));
    }

    private String requireTrimmed(String value, String code, String message) {
        if (value == null || value.trim().isEmpty()) throw new BadRequestException(code, message);
        return value.trim();
    }

    private StoreResponseDto toResponse(Store store) {
        return StoreResponseDto.builder()
                .id(store.getId()).name(store.getName()).code(store.getCode())
                .timezone(store.getTimezone()).active(store.getActive())
                .primaryStore(store.getPrimaryStore()).address(store.getAddress())
                .city(store.getCity()).countryCode(store.getCountryCode())
                .cashReconciliationEnabled(store.getCashReconciliationEnabled()).build();
    }
}
