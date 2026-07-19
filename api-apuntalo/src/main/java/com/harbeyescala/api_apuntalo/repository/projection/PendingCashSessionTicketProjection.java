package com.harbeyescala.api_apuntalo.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface PendingCashSessionTicketProjection {
    Long getTicketId(); Long getCommercialNumber(); Long getMesaId(); Integer getMesaNumero();
    LocalDateTime getCreatedAt(); Long getCreatedById(); String getCreatedByUsername();
    BigDecimal getTotal(); Long getActiveLineCount();
}
