-- F8.6: next_number independiente por Store; no renumera tickets.
DO $$ BEGIN
 IF to_regclass(current_schema()||'.ticket_number_sequences') IS NULL THEN RAISE EXCEPTION 'V8.6: falta ticket_number_sequences'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='ticket_number_sequences'::regclass
   AND conname='pk_ticket_number_sequences' AND contype='p') THEN RAISE EXCEPTION 'V8.6: falta PK esperada de secuencias'; END IF;
 IF to_regclass(current_schema()||'.uk_tickets_negocio_commercial_number') IS NULL THEN RAISE EXCEPTION 'V8.6: falta índice único tenant esperado'; END IF;
 IF EXISTS(SELECT 1 FROM tickets t LEFT JOIN stores s ON s.id=t.store_id AND s.negocio_id=t.negocio_id
   WHERE t.negocio_id IS NULL OR t.store_id IS NULL OR s.id IS NULL) THEN RAISE EXCEPTION 'V8.6: ticket sin Store tenant-safe'; END IF;
 IF EXISTS(SELECT 1 FROM tickets WHERE commercial_number IS NOT NULL AND commercial_number<=0) THEN RAISE EXCEPTION 'V8.6: número inválido'; END IF;
 IF EXISTS(SELECT negocio_id,store_id,commercial_number FROM tickets WHERE commercial_number IS NOT NULL
   GROUP BY negocio_id,store_id,commercial_number HAVING count(*)>1) THEN RAISE EXCEPTION 'V8.6: números duplicados por Store'; END IF;
 IF EXISTS(SELECT 1 FROM ticket_number_sequences q LEFT JOIN negocios n ON n.id=q.negocio_id WHERE n.id IS NULL OR q.next_number<=0) THEN RAISE EXCEPTION 'V8.6: secuencia incoherente'; END IF;
 IF EXISTS(SELECT q.negocio_id FROM ticket_number_sequences q LEFT JOIN stores s
   ON s.negocio_id=q.negocio_id AND s.primary_store GROUP BY q.negocio_id HAVING count(s.id)<>1)
   THEN RAISE EXCEPTION 'V8.6: negocio con secuencia sin una única Store Principal'; END IF;
END $$;
CREATE TEMP TABLE v8_6_ticket_snapshot ON COMMIT DROP AS
 SELECT id,commercial_number,status,total,created_at,updated_at,paid_at,cancelled_at FROM tickets;
CREATE TEMP TABLE v8_6_sequence_snapshot ON COMMIT DROP AS SELECT * FROM ticket_number_sequences;
ALTER TABLE ticket_number_sequences ADD COLUMN store_id bigint;
UPDATE ticket_number_sequences q SET store_id=s.id FROM stores s WHERE s.negocio_id=q.negocio_id AND s.primary_store;
ALTER TABLE ticket_number_sequences ALTER COLUMN store_id SET NOT NULL;
ALTER TABLE ticket_number_sequences DROP CONSTRAINT pk_ticket_number_sequences;
ALTER TABLE ticket_number_sequences ADD CONSTRAINT pk_ticket_number_sequences PRIMARY KEY(negocio_id,store_id);
ALTER TABLE ticket_number_sequences ADD CONSTRAINT fk_ticket_number_sequences_store_tenant
 FOREIGN KEY(store_id,negocio_id) REFERENCES stores(id,negocio_id);
-- Nunca reduce Principal: conserva contador adelantado o max+1, el mayor.
UPDATE ticket_number_sequences q SET next_number=GREATEST(q.next_number,COALESCE(x.max_number,0)+1)
FROM (SELECT negocio_id,store_id,max(commercial_number) max_number FROM tickets GROUP BY negocio_id,store_id) x
WHERE x.negocio_id=q.negocio_id AND x.store_id=q.store_id;
-- Estrategia preventiva para todas las Stores existentes.
INSERT INTO ticket_number_sequences(negocio_id,store_id,next_number,version)
SELECT s.negocio_id,s.id,COALESCE(max(t.commercial_number),0)+1,0
FROM stores s LEFT JOIN tickets t ON t.negocio_id=s.negocio_id AND t.store_id=s.id
GROUP BY s.negocio_id,s.id ON CONFLICT(negocio_id,store_id) DO NOTHING;
DROP INDEX uk_tickets_negocio_commercial_number;
CREATE UNIQUE INDEX uk_tickets_tenant_store_commercial_number
 ON tickets(negocio_id,store_id,commercial_number) WHERE commercial_number IS NOT NULL;
DO $$ BEGIN
 IF (SELECT count(*) FROM tickets)<>(SELECT count(*) FROM v8_6_ticket_snapshot) THEN RAISE EXCEPTION 'V8.6 postflight: conteo alterado'; END IF;
 IF EXISTS(SELECT 1 FROM tickets t FULL JOIN v8_6_ticket_snapshot b USING(id) WHERE t.id IS NULL OR b.id IS NULL
  OR t.commercial_number IS DISTINCT FROM b.commercial_number OR t.status IS DISTINCT FROM b.status
  OR t.total IS DISTINCT FROM b.total OR t.created_at IS DISTINCT FROM b.created_at OR t.updated_at IS DISTINCT FROM b.updated_at
  OR t.paid_at IS DISTINCT FROM b.paid_at OR t.cancelled_at IS DISTINCT FROM b.cancelled_at) THEN RAISE EXCEPTION 'V8.6 postflight: ticket histórico alterado'; END IF;
 IF EXISTS(SELECT s.negocio_id,s.id FROM stores s LEFT JOIN ticket_number_sequences q ON q.negocio_id=s.negocio_id AND q.store_id=s.id
  GROUP BY s.negocio_id,s.id HAVING count(q.store_id)<>1) THEN RAISE EXCEPTION 'V8.6 postflight: secuencia ausente/duplicada'; END IF;
 IF EXISTS(SELECT 1 FROM ticket_number_sequences q LEFT JOIN
  (SELECT negocio_id,store_id,COALESCE(max(commercial_number),0) m FROM tickets GROUP BY negocio_id,store_id) t
  ON t.negocio_id=q.negocio_id AND t.store_id=q.store_id WHERE q.next_number<=COALESCE(t.m,0)) THEN RAISE EXCEPTION 'V8.6 postflight: contador inseguro'; END IF;
 IF EXISTS(SELECT 1 FROM v8_6_sequence_snapshot b JOIN stores s
   ON s.negocio_id=b.negocio_id AND s.primary_store
   LEFT JOIN ticket_number_sequences q ON q.negocio_id=b.negocio_id AND q.store_id=s.id
   WHERE q.next_number IS NULL OR q.next_number<b.next_number)
   THEN RAISE EXCEPTION 'V8.6 postflight: contador histórico reducido'; END IF;
END $$;
