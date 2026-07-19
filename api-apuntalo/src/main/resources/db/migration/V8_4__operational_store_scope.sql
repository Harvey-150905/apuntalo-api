-- F8.4: scope operativo por Store. Carta, numeración, auditoría e idempotencia formal quedan fuera.

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['mesas','cash_registers','cash_sessions','tickets','payments','cash_movements'] LOOP
    IF to_regclass(current_schema() || '.' || t) IS NULL THEN
      RAISE EXCEPTION 'V8.4 abortada: falta tabla operativa %', t;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema()
      AND table_name=t AND column_name='negocio_id') THEN
      RAISE EXCEPTION 'V8.4 abortada: %.negocio_id no existe', t;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema()
      AND table_name=t AND column_name='store_id') THEN
      RAISE EXCEPTION 'V8.4 abortada: %.store_id ya existe; estado parcial', t;
    END IF;
  END LOOP;
  IF EXISTS (SELECT negocio_id FROM stores WHERE primary_store GROUP BY negocio_id HAVING count(*)<>1)
     OR EXISTS (SELECT n.id FROM negocios n LEFT JOIN stores s ON s.negocio_id=n.id AND s.primary_store
                WHERE s.id IS NULL) THEN
    RAISE EXCEPTION 'V8.4 abortada: no existe exactamente una Principal por negocio';
  END IF;
END $$;

CREATE TEMP TABLE v8_4_counts ON COMMIT DROP AS
SELECT 'mesas' n,count(*) c FROM mesas UNION ALL SELECT 'cash_registers',count(*) FROM cash_registers
UNION ALL SELECT 'cash_sessions',count(*) FROM cash_sessions UNION ALL SELECT 'tickets',count(*) FROM tickets
UNION ALL SELECT 'payments',count(*) FROM payments UNION ALL SELECT 'cash_movements',count(*) FROM cash_movements;

ALTER TABLE mesas ADD COLUMN store_id bigint;
ALTER TABLE cash_registers ADD COLUMN store_id bigint;
ALTER TABLE cash_sessions ADD COLUMN store_id bigint;
ALTER TABLE tickets ADD COLUMN store_id bigint;
ALTER TABLE payments ADD COLUMN store_id bigint;
ALTER TABLE cash_movements ADD COLUMN store_id bigint;

UPDATE mesas x SET store_id=s.id FROM stores s WHERE s.negocio_id=x.negocio_id AND s.primary_store;
UPDATE cash_registers x SET store_id=s.id FROM stores s WHERE s.negocio_id=x.negocio_id AND s.primary_store;
UPDATE cash_sessions x SET store_id=s.id FROM stores s WHERE s.negocio_id=x.negocio_id AND s.primary_store;
UPDATE tickets x SET store_id=s.id FROM stores s WHERE s.negocio_id=x.negocio_id AND s.primary_store;
UPDATE payments x SET store_id=s.id FROM stores s WHERE s.negocio_id=x.negocio_id AND s.primary_store;
UPDATE cash_movements x SET store_id=s.id FROM stores s WHERE s.negocio_id=x.negocio_id AND s.primary_store;

DO $$ DECLARE t text; BEGIN
  FOREACH t IN ARRAY ARRAY['mesas','cash_registers','cash_sessions','tickets','payments','cash_movements'] LOOP
    IF EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema=current_schema()
       AND c.table_name=t AND c.column_name='store_id' AND c.is_nullable='YES') THEN
      EXECUTE format('ALTER TABLE %I ALTER COLUMN store_id SET NOT NULL',t);
    END IF;
    EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (store_id,negocio_id) REFERENCES stores(id,negocio_id)',
      t,'fk_'||t||'_store_tenant');
    EXECUTE format('CREATE UNIQUE INDEX %I ON %I(id,negocio_id,store_id)', 'uk_'||t||'_id_tenant_store',t);
  END LOOP;
END $$;

-- Relaciones operativas Store-safe.
ALTER TABLE cash_sessions ADD CONSTRAINT fk_cash_sessions_register_store
  FOREIGN KEY (cash_register_id,negocio_id,store_id) REFERENCES cash_registers(id,negocio_id,store_id);
ALTER TABLE tickets ADD CONSTRAINT fk_tickets_mesa_store
  FOREIGN KEY (mesa_id,negocio_id,store_id) REFERENCES mesas(id,negocio_id,store_id);
ALTER TABLE tickets ADD CONSTRAINT fk_tickets_origin_session_store
  FOREIGN KEY (origin_cash_session_id,negocio_id,store_id) REFERENCES cash_sessions(id,negocio_id,store_id);
ALTER TABLE payments ADD CONSTRAINT fk_payments_ticket_store
  FOREIGN KEY (ticket_id,negocio_id,store_id) REFERENCES tickets(id,negocio_id,store_id);
ALTER TABLE payments ADD CONSTRAINT fk_payments_cash_session_store
  FOREIGN KEY (cash_session_id,negocio_id,store_id) REFERENCES cash_sessions(id,negocio_id,store_id);
ALTER TABLE cash_movements ADD CONSTRAINT fk_cash_movements_session_store
  FOREIGN KEY (cash_session_id,negocio_id,store_id) REFERENCES cash_sessions(id,negocio_id,store_id);

-- Sustituye únicamente unicidades funcionales por sus equivalentes Store-aware.
ALTER TABLE cash_registers DROP CONSTRAINT uk_cash_registers_negocio_normalized_name;
ALTER TABLE cash_registers ADD CONSTRAINT uk_cash_registers_negocio_store_normalized_name
  UNIQUE(negocio_id,store_id,normalized_name);
DO $$ DECLARE c text; BEGIN
  SELECT conname INTO c FROM pg_constraint p WHERE p.conrelid='mesas'::regclass AND p.contype='u'
    AND (SELECT array_agg(a.attname ORDER BY k.ord) FROM unnest(p.conkey) WITH ORDINALITY k(attnum,ord)
         JOIN pg_attribute a ON a.attrelid=p.conrelid AND a.attnum=k.attnum)=ARRAY['negocio_id','numero']::name[];
  IF c IS NOT NULL THEN EXECUTE format('ALTER TABLE mesas DROP CONSTRAINT %I',c); END IF;
END $$;
ALTER TABLE mesas ADD CONSTRAINT uk_mesas_negocio_store_numero UNIQUE(negocio_id,store_id,numero);

CREATE INDEX idx_mesas_tenant_store_active ON mesas(negocio_id,store_id,activa);
CREATE INDEX idx_cash_registers_tenant_store_active ON cash_registers(negocio_id,store_id,active);
CREATE INDEX idx_cash_sessions_tenant_store_status ON cash_sessions(negocio_id,store_id,status,opened_at);
CREATE INDEX idx_cash_sessions_tenant_store_register ON cash_sessions(negocio_id,store_id,cash_register_id);
CREATE INDEX idx_tickets_tenant_store_status ON tickets(negocio_id,store_id,status);
CREATE INDEX idx_tickets_tenant_store_paid ON tickets(negocio_id,store_id,paid_at);
CREATE INDEX idx_payments_tenant_store_session ON payments(negocio_id,store_id,cash_session_id);
CREATE INDEX idx_payments_tenant_store_paid ON payments(negocio_id,store_id,paid_at);
CREATE INDEX idx_movements_tenant_store_session ON cash_movements(negocio_id,store_id,cash_session_id,performed_at,id);

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM mesas x JOIN stores s ON s.id=x.store_id WHERE NOT s.primary_store)
    OR EXISTS (SELECT 1 FROM cash_registers x JOIN stores s ON s.id=x.store_id WHERE NOT s.primary_store)
    OR EXISTS (SELECT 1 FROM cash_sessions x JOIN stores s ON s.id=x.store_id WHERE NOT s.primary_store)
    OR EXISTS (SELECT 1 FROM tickets x JOIN stores s ON s.id=x.store_id WHERE NOT s.primary_store)
    OR EXISTS (SELECT 1 FROM payments x JOIN stores s ON s.id=x.store_id WHERE NOT s.primary_store)
    OR EXISTS (SELECT 1 FROM cash_movements x JOIN stores s ON s.id=x.store_id WHERE NOT s.primary_store) THEN
    RAISE EXCEPTION 'V8.4 postflight: un histórico no quedó en la Store Principal';
  END IF;
  IF EXISTS (SELECT 1 FROM mesas m JOIN stores s ON s.id=m.store_id WHERE s.negocio_id<>m.negocio_id)
    OR EXISTS (SELECT 1 FROM cash_registers r JOIN stores s ON s.id=r.store_id WHERE s.negocio_id<>r.negocio_id)
    OR EXISTS (SELECT 1 FROM cash_sessions x JOIN cash_registers r ON r.id=x.cash_register_id WHERE x.store_id<>r.store_id)
    OR EXISTS (SELECT 1 FROM tickets t JOIN mesas m ON m.id=t.mesa_id WHERE t.store_id<>m.store_id)
    OR EXISTS (SELECT 1 FROM payments p JOIN tickets t ON t.id=p.ticket_id WHERE p.store_id<>t.store_id)
    OR EXISTS (SELECT 1 FROM payments p JOIN cash_sessions s ON s.id=p.cash_session_id WHERE p.store_id<>s.store_id)
    OR EXISTS (SELECT 1 FROM cash_movements m JOIN cash_sessions s ON s.id=m.cash_session_id WHERE m.store_id<>s.store_id) THEN
    RAISE EXCEPTION 'V8.4 postflight: relación tenant/Store cruzada';
  END IF;
  IF (SELECT count(*) FROM mesas)<>(SELECT c FROM v8_4_counts WHERE n='mesas')
    OR (SELECT count(*) FROM cash_registers)<>(SELECT c FROM v8_4_counts WHERE n='cash_registers')
    OR (SELECT count(*) FROM cash_sessions)<>(SELECT c FROM v8_4_counts WHERE n='cash_sessions')
    OR (SELECT count(*) FROM tickets)<>(SELECT c FROM v8_4_counts WHERE n='tickets')
    OR (SELECT count(*) FROM payments)<>(SELECT c FROM v8_4_counts WHERE n='payments')
    OR (SELECT count(*) FROM cash_movements)<>(SELECT c FROM v8_4_counts WHERE n='cash_movements') THEN
    RAISE EXCEPTION 'V8.4 postflight: cambió un conteo operativo';
  END IF;
END $$;
