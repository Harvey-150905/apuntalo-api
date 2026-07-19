package com.harbeyescala.api_apuntalo.exception;
import lombok.Getter; import java.math.BigDecimal;
@Getter public class PendingTicketsAcknowledgementException extends RuntimeException {
 private final Long pendingTicketCount; private final BigDecimal pendingTicketAmount;
 public PendingTicketsAcknowledgementException(Long count,BigDecimal amount){super("Debe confirmar los tickets pendientes");this.pendingTicketCount=count;this.pendingTicketAmount=amount;}
}
