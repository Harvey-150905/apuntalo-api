package com.harbeyescala.api_apuntalo.controller;
import com.harbeyescala.api_apuntalo.dto.*; import com.harbeyescala.api_apuntalo.service.*;
import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import java.util.*;
import jakarta.validation.Valid;
@RestController @RequestMapping("/api/cash-sessions")
public class CashSessionOperationController {
 private final CashSessionOperationService service; private final IdempotencyService idempotency;
 public CashSessionOperationController(CashSessionOperationService service,IdempotencyService idempotency){this.service=service;this.idempotency=idempotency;}
 @PostMapping("/{id}/movements") public ResponseEntity<CashMovementResponseDto> movement(@PathVariable Long id,@Valid @RequestBody CashMovementRequestDto body,@RequestHeader(value="Idempotency-Key",required=false)String key){
  Map<String,Object> hashBody=new LinkedHashMap<>();hashBody.put("type",body.getType());hashBody.put("amount",body.getAmount());hashBody.put("reason",body.getReason()==null?null:body.getReason().trim());
  IdempotentOutcome<CashMovementResponseDto> o=idempotency.execute("CASH_MOVEMENT_CREATE","CASH_MOVEMENT",key,Map.of("cashSessionId",id,"body",hashBody),201,CashMovementResponseDto.class,()->service.createMovement(id,body));return response(o);
 }
 @GetMapping("/{id}/movements") public PageResponseDto<CashMovementResponseDto> movements(
  @PathVariable Long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size){
  return service.listMovements(id,page,size);
 }
 @PostMapping("/{id}/close") public ResponseEntity<CashSessionCloseResponseDto> close(@PathVariable Long id,@Valid @RequestBody CloseCashSessionRequestDto body,@RequestHeader(value="Idempotency-Key",required=false)String key){
  IdempotentOutcome<CashSessionCloseResponseDto> o=idempotency.execute("CASH_SESSION_CLOSE","CASH_SESSION",key,Map.of("cashSessionId",id,"body",body),200,CashSessionCloseResponseDto.class,()->service.close(id,body));return response(o);
 }
 private <T> ResponseEntity<T> response(IdempotentOutcome<T> o){ResponseEntity.BodyBuilder b=ResponseEntity.status(o.status());if(o.replayed())b.header("Idempotency-Replayed","true");return b.body(o.body());}
}
