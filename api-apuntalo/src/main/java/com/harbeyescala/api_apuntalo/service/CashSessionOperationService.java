package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.dto.*;
import com.harbeyescala.api_apuntalo.entity.*;
import com.harbeyescala.api_apuntalo.entity.enums.*;
import com.harbeyescala.api_apuntalo.exception.*;
import com.harbeyescala.api_apuntalo.repository.*;
import com.harbeyescala.api_apuntalo.repository.projection.PendingTicketAggregateProjection;
import com.harbeyescala.api_apuntalo.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.math.*; import java.time.LocalDateTime; import java.util.*;

@Service
public class CashSessionOperationService {
 private static final BigDecimal MAX_AMOUNT=new BigDecimal("99999999.99");
 private final CashSessionRepository sessions; private final CashMovementRepository movements;
 private final PaymentRepository payments; private final TicketRepository tickets; private final UserRepository users;
 private final CurrentUser current; private final AuditEventService audit; private final BusinessTimeService businessTime;
 public CashSessionOperationService(CashSessionRepository sessions,CashMovementRepository movements,
  PaymentRepository payments,TicketRepository tickets,UserRepository users,CurrentUser current,AuditEventService audit,BusinessTimeService businessTime){
  this.sessions=sessions;this.movements=movements;this.payments=payments;this.tickets=tickets;this.users=users;this.current=current;this.audit=audit;this.businessTime=businessTime;
 }

 @Transactional
 public CashMovementResponseDto createMovement(Long sessionId,CashMovementRequestDto request){
  Long tenant=current.getTenantId(); CashSession session=lockOpen(sessionId,tenant,false); authorize(session,"CASH_MOVEMENT_NOT_ALLOWED");
  CashMovementType type=request.getType(); if(type==null) throw new BusinessRuleException("INVALID_CASH_MOVEMENT_TYPE","El tipo de movimiento es obligatorio");
  BigDecimal amount=money(request.getAmount(),"INVALID_CASH_MOVEMENT_AMOUNT");
  if(amount.signum()<=0) throw new BusinessRuleException("INVALID_CASH_MOVEMENT_AMOUNT","El importe debe ser mayor que cero");
  String reason=request.getReason()==null?null:request.getReason().trim();
  if(reason==null||reason.isEmpty()) throw new BusinessRuleException("CASH_MOVEMENT_REASON_REQUIRED","El motivo es obligatorio");
  if(reason.length()>300) throw new BusinessRuleException("CASH_MOVEMENT_REASON_TOO_LONG","El motivo no puede superar 300 caracteres");
  Totals before=totals(session,tenant);
  BigDecimal after=type==CashMovementType.CASH_IN?before.expectedCash().add(amount):before.expectedCash().subtract(amount);
  if(after.signum()<0) throw new BusinessRuleException("INSUFFICIENT_EXPECTED_CASH","La salida dejaría el efectivo esperado por debajo de cero");
  User actor=actor(tenant); LocalDateTime now=businessTime.nowForStorage();
  CashMovement movement=movements.saveAndFlush(CashMovement.builder().negocio(session.getNegocio()).store(session.getStore()).cashSession(session)
    .type(type).amount(amount).reason(reason).performedBy(actor).performedAt(now).createdAt(now).build());
  Map<String,Object> meta=movementMetadata(session,actor,type,amount,reason,after);
  audit.recordSuccess(AuditEntityType.CASH_SESSION,session.getId(),type==CashMovementType.CASH_IN?
    AuditAction.CASH_MOVEMENT_IN_CREATED:AuditAction.CASH_MOVEMENT_OUT_CREATED,null,meta);
  return movementResponse(movement,after);
 }

 @Transactional(readOnly=true)
 public PageResponseDto<CashMovementResponseDto> listMovements(Long sessionId,int page,int size){
  PaginationPolicy.validate(page,size);
  Long tenant=current.getTenantId(); CashSession session=sessions.findByIdAndNegocioIdAndStoreId(sessionId,tenant,current.requireCurrentStoreId()).orElseThrow(CashSessionNotFoundException::new);
  authorize(session,"CASH_MOVEMENT_NOT_ALLOWED");
  PageRequest pageable=PageRequest.of(page,size,Sort.by(Sort.Order.desc("performedAt"),Sort.Order.desc("id")));
  Page<CashMovement> result=movements.findByCashSessionIdAndNegocioIdAndStoreId(sessionId,tenant,current.requireCurrentStoreId(),pageable);
  List<CashMovementResponseDto> content=result.getContent().stream().map(m->movementResponse(m,null)).toList();
  return new PageResponseDto<>(content,result.getNumber(),result.getSize(),result.getTotalElements(),result.getTotalPages(),result.isLast());
 }

 @Transactional
 public CashSessionCloseResponseDto close(Long sessionId,CloseCashSessionRequestDto request){
  Long tenant=current.getTenantId(); CashSession session=lockOpen(sessionId,tenant,true); authorize(session,"CASH_SESSION_CLOSE_NOT_ALLOWED");
  User actor=actor(tenant); Totals totals=totals(session,tenant);
  PendingTicketAggregateProjection pending=tickets.aggregatePendingByOriginSession(tenant,sessionId,TicketStatus.OPEN);
  long pendingCount=pending.getTicketCount()==null?0:pending.getTicketCount();
  BigDecimal pendingAmount=pending.getTotalAmount()==null?BigDecimal.ZERO:pending.getTotalAmount().setScale(2,RoundingMode.HALF_UP);
  boolean acknowledged=Boolean.TRUE.equals(request.getAcknowledgePendingTickets());
  if(pendingCount>0&&!acknowledged) throw new PendingTicketsAcknowledgementException(pendingCount,pendingAmount);
  BigDecimal counted=null,difference=null;
  if(Boolean.TRUE.equals(session.getReconciliationRequired())){
   if(request.getCountedCash()==null) throw new BusinessRuleException("COUNTED_CASH_REQUIRED","El efectivo contado es obligatorio");
   counted=money(request.getCountedCash(),"INVALID_COUNTED_CASH"); if(counted.signum()<0) throw new BusinessRuleException("INVALID_COUNTED_CASH","El efectivo contado no puede ser negativo");
   difference=counted.subtract(totals.expectedCash()).setScale(2,RoundingMode.HALF_UP);
  } else if(request.getCountedCash()!=null) throw new BusinessRuleException("COUNTED_CASH_NOT_ALLOWED","Esta sesión no requiere conciliación");
  LocalDateTime now=businessTime.nowForStorage(); boolean responsible=session.getOpenedBy().getId().equals(actor.getId());
  session.setClosedBy(actor);session.setClosedAt(now);session.setCloseMode(responsible?CashSessionCloseMode.RESPONSIBLE:CashSessionCloseMode.SUPERVISED);
  session.setExpectedCashAtClose(totals.expectedCash());session.setCountedCash(counted);session.setDifference(difference);
  session.setPendingTicketCountAtClose(pendingCount);session.setPendingTicketAmountAtClose(pendingAmount);
  session.setPendingTicketsAcknowledged(pendingCount>0&&acknowledged);session.setStatus(CashSessionStatus.CLOSED);sessions.saveAndFlush(session);
  CashSessionCloseResponseDto response=closeResponse(session,totals);
  audit.recordSuccess(AuditEntityType.CASH_SESSION,sessionId,AuditAction.CASH_SESSION_CLOSED,null,closeMetadata(response));
  return response;
 }

 private CashSession lockOpen(Long id,Long tenant,boolean closing){
  CashSession s=sessions.findByIdAndNegocioIdAndStoreIdForUpdate(id,tenant,current.requireCurrentStoreId()).orElseThrow(CashSessionNotFoundException::new);
  if(s.getStatus()!=CashSessionStatus.OPEN) throw new ConflictException(closing?"CASH_SESSION_ALREADY_CLOSED":"CASH_SESSION_CLOSED","La sesión de caja está cerrada"); return s;
 }
 private void authorize(CashSession s,String code){ if(current.getRole()==Role.CAMARERO&&!s.getOpenedBy().getId().equals(current.getUserId())) throw new ForbiddenOperationException(code,"No tienes permisos sobre esta sesión de caja"); }
 private User actor(Long tenant){return users.findByIdAndNegocioId(current.getUserId(),tenant).orElseThrow(()->new ResourceNotFoundException("Usuario no encontrado"));}
 private BigDecimal money(BigDecimal v,String code){return MoneyPolicy.requireValid(v,code,"Importe no válido");}
 private Totals totals(CashSession s,Long tenant){Long store=s.getStore().getId();BigDecimal cash=payments.sumAmountBySession(tenant,store,s.getId(),PaymentMethod.CASH);BigDecimal card=payments.sumAmountBySession(tenant,store,s.getId(),PaymentMethod.CARD);BigDecimal in=movements.sumAmount(s.getId(),tenant,store,CashMovementType.CASH_IN);BigDecimal out=movements.sumAmount(s.getId(),tenant,store,CashMovementType.CASH_OUT);return new Totals(cash,card,in,out,s.getOpeningFloat().add(cash).add(in).subtract(out));}
 private record Totals(BigDecimal cashSales,BigDecimal cardSales,BigDecimal cashIn,BigDecimal cashOut,BigDecimal expectedCash){}
 private CashMovementResponseDto movementResponse(CashMovement m,BigDecimal expected){return CashMovementResponseDto.builder().movementId(m.getId()).cashSessionId(m.getCashSession().getId()).type(m.getType()).amount(m.getAmount()).reason(m.getReason()).performedById(m.getPerformedBy().getId()).performedByUsername(m.getPerformedBy().getUsername()).performedAt(m.getPerformedAt()).expectedCashAfterMovement(expected).build();}
 private Map<String,Object> movementMetadata(CashSession s,User a,CashMovementType t,BigDecimal amount,String reason,BigDecimal expected){Map<String,Object> m=new LinkedHashMap<>();m.put("cashSessionId",s.getId());m.put("cashRegisterId",s.getCashRegister().getId());m.put("cashRegisterName",s.getCashRegister().getName());m.put("responsibleId",s.getOpenedBy().getId());m.put("responsibleUsername",s.getOpenedBy().getUsername());m.put("actorId",a.getId());m.put("actorUsername",a.getUsername());m.put("type",t);m.put("amount",amount);m.put("reason",reason);m.put("expectedCashAfterMovement",expected);return m;}
 private CashSessionCloseResponseDto closeResponse(CashSession s,Totals t){return CashSessionCloseResponseDto.builder().cashSessionId(s.getId()).status(s.getStatus()).cashRegisterId(s.getCashRegister().getId()).cashRegisterName(s.getCashRegister().getName()).responsibleId(s.getOpenedBy().getId()).responsibleUsername(s.getOpenedBy().getUsername()).openedAt(s.getOpenedAt()).openingFloat(s.getOpeningFloat()).reconciliationRequired(s.getReconciliationRequired()).closedById(s.getClosedBy().getId()).closedByUsername(s.getClosedBy().getUsername()).closedAt(s.getClosedAt()).closeMode(s.getCloseMode()).cashSales(t.cashSales()).cardSales(t.cardSales()).totalSales(t.cashSales().add(t.cardSales())).cashIn(t.cashIn()).cashOut(t.cashOut()).expectedCashAtClose(s.getExpectedCashAtClose()).countedCash(s.getCountedCash()).difference(s.getDifference()).pendingTicketCountAtClose(s.getPendingTicketCountAtClose()).pendingTicketAmountAtClose(s.getPendingTicketAmountAtClose()).pendingTicketsAcknowledged(s.getPendingTicketsAcknowledged()).build();}
 private Map<String,Object> closeMetadata(CashSessionCloseResponseDto r){Map<String,Object> m=new LinkedHashMap<>();m.put("cashSessionId",r.getCashSessionId());m.put("cashRegisterId",r.getCashRegisterId());m.put("cashRegisterName",r.getCashRegisterName());m.put("responsibleId",r.getResponsibleId());m.put("responsibleUsername",r.getResponsibleUsername());m.put("closedById",r.getClosedById());m.put("closedByUsername",r.getClosedByUsername());m.put("closeMode",r.getCloseMode());m.put("reconciliationRequired",r.getReconciliationRequired());m.put("openingFloat",r.getOpeningFloat());m.put("cashSales",r.getCashSales());m.put("cardSales",r.getCardSales());m.put("cashIn",r.getCashIn());m.put("cashOut",r.getCashOut());m.put("totalSales",r.getTotalSales());m.put("expectedCashAtClose",r.getExpectedCashAtClose());m.put("countedCash",r.getCountedCash());m.put("difference",r.getDifference());m.put("pendingTicketCountAtClose",r.getPendingTicketCountAtClose());m.put("pendingTicketAmountAtClose",r.getPendingTicketAmountAtClose());m.put("pendingTicketsAcknowledged",r.getPendingTicketsAcknowledged());m.put("openedAt",r.getOpenedAt());m.put("closedAt",r.getClosedAt());return m;}
}
