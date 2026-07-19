package com.harbeyescala.api_apuntalo.repository.projection;
import java.math.BigDecimal;
public interface PendingTicketAggregateProjection { Long getTicketCount(); BigDecimal getTotalAmount(); }
