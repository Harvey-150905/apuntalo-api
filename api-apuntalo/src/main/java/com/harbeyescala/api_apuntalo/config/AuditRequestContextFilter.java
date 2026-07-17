package com.harbeyescala.api_apuntalo.config;

import com.harbeyescala.api_apuntalo.security.AuditRequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Rellena {@link AuditRequestContext} con un identificador de correlación
 * por petición (cabecera {@code X-Request-Id} si el cliente la envía, o uno
 * generado) y la {@code Idempotency-Key} si está presente (Fase 5.3). Se
 * limpia siempre en el {@code finally} para no filtrar contexto entre
 * peticiones que reutilicen el mismo hilo.
 */
@Component
public class AuditRequestContextFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final AuditRequestContext auditRequestContext;

    public AuditRequestContextFilter(AuditRequestContext auditRequestContext) {
        this.auditRequestContext = auditRequestContext;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);

        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        String idempotencyKey = request.getHeader(IDEMPOTENCY_HEADER);

        try {
            auditRequestContext.set(requestId, idempotencyKey);
            filterChain.doFilter(request, response);
        } finally {
            auditRequestContext.clear();
        }
    }
}
