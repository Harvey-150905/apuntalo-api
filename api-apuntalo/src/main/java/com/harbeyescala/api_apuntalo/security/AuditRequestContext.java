package com.harbeyescala.api_apuntalo.security;

import org.springframework.stereotype.Component;

/**
 * Contexto de la petición HTTP actual relevante para auditoría (Fase 5.3):
 * identificador de correlación de la petición y, si se envió, la
 * Idempotency-Key usada. Se rellena en
 * {@link com.harbeyescala.api_apuntalo.config.AuditRequestContextFilter} y
 * se lee, en el mismo hilo, desde los servicios de auditoría.
 *
 * Usa un {@link ThreadLocal} (mismo patrón que {@code SecurityContextHolder})
 * porque los filtros de servlet y el código de negocio comparten hilo en
 * el modelo de peticiones síncronas de Spring MVC.
 */
@Component
public class AuditRequestContext {

    private record Context(String requestId, String idempotencyKey) {
    }

    private static final ThreadLocal<Context> HOLDER = new ThreadLocal<>();

    public void set(String requestId, String idempotencyKey) {
        HOLDER.set(new Context(requestId, idempotencyKey));
    }

    public void clear() {
        HOLDER.remove();
    }

    public String getRequestId() {
        Context context = HOLDER.get();
        return context != null ? context.requestId() : null;
    }

    public String getIdempotencyKey() {
        Context context = HOLDER.get();
        return context != null ? context.idempotencyKey() : null;
    }
}
