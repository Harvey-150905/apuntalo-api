-- Apúntalo Fase 5.3: auditoría funcional segura. PostgreSQL, convergente y no destructiva.
CREATE TABLE IF NOT EXISTS audit_events (id bigserial);

DO $$
DECLARE mismatch text;
BEGIN
    SELECT string_agg(e.column_name || ' expected ' || e.data_type || ' but found ' || c.data_type, '; ')
      INTO mismatch
      FROM (VALUES
        ('id','bigint'), ('negocio_id','bigint'), ('user_id','bigint'),
        ('entity_type','character varying'), ('entity_id','bigint'), ('action','character varying'),
        ('previous_state_json','text'), ('new_state_json','text'),
        ('occurred_at','timestamp without time zone'), ('idempotency_key','character varying'),
        ('request_id','character varying'), ('success','boolean'), ('error_code','character varying'),
        ('metadata_json','text')
      ) AS e(column_name, data_type)
      JOIN information_schema.columns c ON c.table_schema = current_schema()
       AND c.table_name = 'audit_events' AND c.column_name = e.column_name
     WHERE c.data_type <> e.data_type;
    IF mismatch IS NOT NULL THEN
        RAISE EXCEPTION 'Migración audit_events abortada por tipos incompatibles: %', mismatch;
    END IF;
END $$;

ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS id bigserial;
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS negocio_id bigint;
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS user_id bigint;
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS entity_type varchar(80);
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS entity_id bigint;
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS action varchar(100);
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS previous_state_json text;
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS new_state_json text;
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS occurred_at timestamp;
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS idempotency_key varchar(100);
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS request_id varchar(100);
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS success boolean;
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS error_code varchar(100);
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS metadata_json text;

DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM audit_events WHERE negocio_id IS NULL OR entity_type IS NULL OR action IS NULL OR success IS NULL) THEN
        RAISE EXCEPTION 'Migración audit_events abortada: existen filas sin tenant/entity/action/success';
    END IF;
END $$;

UPDATE audit_events SET occurred_at = now() WHERE occurred_at IS NULL;
ALTER TABLE audit_events ALTER COLUMN negocio_id SET NOT NULL;
ALTER TABLE audit_events ALTER COLUMN entity_type SET NOT NULL;
ALTER TABLE audit_events ALTER COLUMN action SET NOT NULL;
ALTER TABLE audit_events ALTER COLUMN occurred_at SET DEFAULT now();
ALTER TABLE audit_events ALTER COLUMN occurred_at SET NOT NULL;
ALTER TABLE audit_events ALTER COLUMN success SET NOT NULL;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'audit_events'::regclass AND contype = 'p') THEN
        ALTER TABLE audit_events ADD CONSTRAINT pk_audit_events PRIMARY KEY (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_audit_events_negocio'
                   AND conrelid = 'audit_events'::regclass) THEN
        ALTER TABLE audit_events ADD CONSTRAINT fk_audit_events_negocio
            FOREIGN KEY (negocio_id) REFERENCES negocios(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_audit_events_user'
                   AND conrelid = 'audit_events'::regclass) THEN
        ALTER TABLE audit_events ADD CONSTRAINT fk_audit_events_user
            FOREIGN KEY (user_id) REFERENCES users(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_audit_events_negocio_occurred ON audit_events (negocio_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_events_entity ON audit_events (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_events_action ON audit_events (action);
CREATE INDEX IF NOT EXISTS idx_audit_events_idempotency_key ON audit_events (idempotency_key)
    WHERE idempotency_key IS NOT NULL;
