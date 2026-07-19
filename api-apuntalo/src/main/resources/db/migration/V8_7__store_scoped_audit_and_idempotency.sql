-- F8.7: scope de Store persistido para auditoría e idempotencia.
DO $$ BEGIN
  IF to_regclass(current_schema()||'.audit_events') IS NULL
     OR to_regclass(current_schema()||'.idempotency_records') IS NULL THEN
    RAISE EXCEPTION 'V8.7: faltan tablas de auditoría/idempotencia';
  END IF;
  IF EXISTS(SELECT 1 FROM audit_events a LEFT JOIN negocios n ON n.id=a.negocio_id WHERE n.id IS NULL)
     OR EXISTS(SELECT 1 FROM idempotency_records r LEFT JOIN negocios n ON n.id=r.tenant_id WHERE n.id IS NULL) THEN
    RAISE EXCEPTION 'V8.7: existe tenant inválido';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='idempotency_records'::regclass
                AND conname='uk_idempotency_scope' AND contype='u') THEN
    RAISE EXCEPTION 'V8.7: falta unicidad idempotente esperada';
  END IF;
  IF EXISTS(SELECT 1 FROM audit_events WHERE action NOT IN
    ('TICKET_CREATED','TICKET_LINES_ADDED','LINE_DISCOUNT_APPLIED','LINE_DISCOUNT_REMOVED',
     'TICKET_LINE_CANCELLED','TICKET_BATCH_CANCELLED','TICKET_CANCELLED','TICKET_TABLE_CHANGED',
     'TICKET_PAID','COMMERCIAL_NUMBER_ASSIGNED','CASH_MANAGEMENT_ENABLED','CASH_MANAGEMENT_DISABLED',
     'CASH_REGISTER_CREATED','CASH_REGISTER_RENAMED','CASH_REGISTER_ACTIVATED','CASH_REGISTER_DEACTIVATED',
     'CASH_SESSION_OPENED','CASH_RECONCILIATION_ENABLED','CASH_RECONCILIATION_DISABLED',
     'CASH_MOVEMENT_IN_CREATED','CASH_MOVEMENT_OUT_CREATED','CASH_SESSION_CLOSED')) THEN
    RAISE EXCEPTION 'V8.7: acción de auditoría no clasificada';
  END IF;
  IF EXISTS(SELECT 1 FROM idempotency_records WHERE operation NOT IN
    ('TICKET_CREATE','TICKET_ADD_LINES','TICKET_PAY','TICKET_CANCEL_LINE','TICKET_CANCEL',
     'APPLY_LINE_DISCOUNT','TICKET_CANCEL_BATCH','TICKET_CHANGE_MESA','CASH_SESSION_OPEN',
     'CASH_SESSION_CLOSE','CASH_MOVEMENT_CREATE','CASH_REGISTER_CREATE','CASH_REGISTER_UPDATE',
     'CASH_REGISTER_STATUS_UPDATE','CASH_MANAGEMENT_CONFIG_UPDATE')) THEN
    RAISE EXCEPTION 'V8.7: operación idempotente no clasificada';
  END IF;
END $$;

CREATE TEMP TABLE v8_7_audit_snapshot ON COMMIT DROP AS SELECT * FROM audit_events;
CREATE TEMP TABLE v8_7_idempotency_snapshot ON COMMIT DROP AS SELECT * FROM idempotency_records;

ALTER TABLE audit_events ADD COLUMN store_id bigint;
ALTER TABLE audit_events ADD COLUMN scope_type varchar(10);
ALTER TABLE audit_events ADD COLUMN store_scope_legacy boolean NOT NULL DEFAULT false;
ALTER TABLE idempotency_records ADD COLUMN store_id bigint;
ALTER TABLE idempotency_records ADD COLUMN scope_type varchar(10);
ALTER TABLE idempotency_records ADD COLUMN store_scope_legacy boolean NOT NULL DEFAULT false;

-- Globales inequívocos del modelo histórico.
UPDATE audit_events SET scope_type='TENANT'
 WHERE action IN ('CASH_MANAGEMENT_ENABLED','CASH_MANAGEMENT_DISABLED');

-- Operativos: la relación persistida y tenant-safe es la fuente oficial.
UPDATE audit_events a SET store_id=t.store_id,scope_type='STORE' FROM tickets t
 WHERE a.negocio_id=t.negocio_id AND a.entity_type='TICKET' AND a.entity_id=t.id;
UPDATE audit_events a SET store_id=t.store_id,scope_type='STORE' FROM ticket_lines l JOIN tickets t ON t.id=l.ticket_id
 WHERE a.negocio_id=t.negocio_id AND a.entity_type='TICKET_LINE' AND a.entity_id=l.id;
UPDATE audit_events a SET store_id=r.store_id,scope_type='STORE' FROM cash_registers r
 WHERE a.negocio_id=r.negocio_id AND a.entity_type='CASH_REGISTER' AND a.entity_id=r.id;
UPDATE audit_events a SET store_id=s.store_id,scope_type='STORE' FROM cash_sessions s
 WHERE a.negocio_id=s.negocio_id AND a.entity_type='CASH_SESSION' AND a.entity_id=s.id;
-- Reconciliación F8.4 usa NEGOCIO/entity_id=Store; las acciones tenant antiguas ya se excluyeron.
UPDATE audit_events a SET store_id=s.id,scope_type='STORE' FROM stores s
 WHERE a.negocio_id=s.negocio_id AND a.entity_type='NEGOCIO' AND a.entity_id=s.id
   AND a.action IN ('CASH_RECONCILIATION_ENABLED','CASH_RECONCILIATION_DISABLED');
UPDATE audit_events SET scope_type='STORE',store_scope_legacy=true
 WHERE scope_type IS NULL;

-- Solo se atribuye Store cuando resource_type/id identifica una entidad validable.
UPDATE idempotency_records r SET store_id=t.store_id,scope_type='STORE' FROM tickets t
 WHERE r.tenant_id=t.negocio_id AND r.resource_type='TICKET' AND r.resource_id=t.id;
UPDATE idempotency_records r SET store_id=s.store_id,scope_type='STORE' FROM cash_sessions s
 WHERE r.tenant_id=s.negocio_id AND r.resource_type='CASH_SESSION' AND r.resource_id=s.id;
UPDATE idempotency_records r SET store_id=c.store_id,scope_type='STORE' FROM cash_registers c
 WHERE r.tenant_id=c.negocio_id AND r.resource_type='CASH_REGISTER' AND r.resource_id=c.id;
UPDATE idempotency_records SET scope_type='STORE',store_scope_legacy=true WHERE scope_type IS NULL;

ALTER TABLE audit_events ALTER COLUMN scope_type SET NOT NULL;
ALTER TABLE idempotency_records ALTER COLUMN scope_type SET NOT NULL;
ALTER TABLE audit_events ADD CONSTRAINT ck_audit_events_scope_type CHECK(scope_type IN('TENANT','STORE'));
ALTER TABLE audit_events ADD CONSTRAINT ck_audit_events_store_scope CHECK(
  (scope_type='TENANT' AND store_id IS NULL AND NOT store_scope_legacy)
  OR (scope_type='STORE' AND store_id IS NOT NULL AND NOT store_scope_legacy)
  OR (scope_type='STORE' AND store_id IS NULL AND store_scope_legacy));
ALTER TABLE idempotency_records ADD CONSTRAINT ck_idempotency_records_scope_type CHECK(scope_type IN('TENANT','STORE'));
ALTER TABLE idempotency_records ADD CONSTRAINT ck_idempotency_records_store_scope CHECK(
  (scope_type='TENANT' AND store_id IS NULL AND NOT store_scope_legacy)
  OR (scope_type='STORE' AND store_id IS NOT NULL AND NOT store_scope_legacy)
  OR (scope_type='STORE' AND store_id IS NULL AND store_scope_legacy));
ALTER TABLE audit_events ADD CONSTRAINT fk_audit_events_store_tenant
  FOREIGN KEY(store_id,negocio_id) REFERENCES stores(id,negocio_id);
ALTER TABLE idempotency_records ADD CONSTRAINT fk_idempotency_records_store_tenant
  FOREIGN KEY(store_id,tenant_id) REFERENCES stores(id,negocio_id);

ALTER TABLE idempotency_records DROP CONSTRAINT uk_idempotency_scope;
CREATE UNIQUE INDEX uk_idempotency_store_scope ON idempotency_records
  (tenant_id,store_id,user_id,operation,idempotency_key) WHERE store_id IS NOT NULL;
CREATE UNIQUE INDEX uk_idempotency_tenant_scope ON idempotency_records
  (tenant_id,user_id,operation,idempotency_key) WHERE store_id IS NULL AND scope_type='TENANT';
-- Legacy ambiguo conserva identidad histórica sin inventar Store.
CREATE UNIQUE INDEX uk_idempotency_legacy_scope ON idempotency_records
  (tenant_id,user_id,operation,idempotency_key) WHERE store_scope_legacy;
CREATE INDEX idx_audit_events_tenant_store_occurred ON audit_events(negocio_id,store_id,occurred_at DESC,id DESC);

DO $$ BEGIN
  IF (SELECT count(*) FROM audit_events)<>(SELECT count(*) FROM v8_7_audit_snapshot)
     OR (SELECT count(*) FROM idempotency_records)<>(SELECT count(*) FROM v8_7_idempotency_snapshot) THEN
    RAISE EXCEPTION 'V8.7 postflight: cambió un conteo histórico';
  END IF;
  IF EXISTS(SELECT 1 FROM audit_events a JOIN stores s ON s.id=a.store_id WHERE s.negocio_id<>a.negocio_id)
     OR EXISTS(SELECT 1 FROM idempotency_records r JOIN stores s ON s.id=r.store_id WHERE s.negocio_id<>r.tenant_id) THEN
    RAISE EXCEPTION 'V8.7 postflight: scope Store/tenant cruzado';
  END IF;
  IF EXISTS(SELECT 1 FROM audit_events a FULL JOIN v8_7_audit_snapshot b USING(id)
    WHERE a.id IS NULL OR b.id IS NULL OR a.action IS DISTINCT FROM b.action
      OR a.metadata_json IS DISTINCT FROM b.metadata_json OR a.occurred_at IS DISTINCT FROM b.occurred_at)
     OR EXISTS(SELECT 1 FROM idempotency_records r FULL JOIN v8_7_idempotency_snapshot b USING(id)
    WHERE r.id IS NULL OR b.id IS NULL OR r.idempotency_key IS DISTINCT FROM b.idempotency_key
      OR r.request_hash IS DISTINCT FROM b.request_hash OR r.response_body IS DISTINCT FROM b.response_body
      OR r.response_status IS DISTINCT FROM b.response_status OR r.status IS DISTINCT FROM b.status
      OR r.created_at IS DISTINCT FROM b.created_at OR r.completed_at IS DISTINCT FROM b.completed_at
      OR r.expires_at IS DISTINCT FROM b.expires_at) THEN
    RAISE EXCEPTION 'V8.7 postflight: contenido histórico alterado';
  END IF;
END $$;
