-- Apúntalo Fase 5.1/5.2: numeración comercial y descuentos por línea.
-- PostgreSQL. Repetible, convergente y no destructiva. Requiere ddl-auto=validate.
-- Abortar si columnas preexistentes tienen tipos incompatibles.
DO $$
DECLARE mismatch text;
BEGIN
    SELECT string_agg(e.table_name || '.' || e.column_name || ' expected ' || e.data_type || ' but found ' || c.data_type, '; ')
      INTO mismatch
      FROM (VALUES
        ('tickets','commercial_number','bigint'),
        ('ticket_lines','discount_percentage','integer'),
        ('ticket_lines','subtotal_before_discount','numeric'),
        ('ticket_lines','discount_amount','numeric'),
        ('ticket_lines','discount_applied_by','bigint'),
        ('ticket_lines','discount_applied_at','timestamp without time zone'),
        ('ticket_number_sequences','negocio_id','bigint'),
        ('ticket_number_sequences','next_number','bigint'),
        ('ticket_number_sequences','version','bigint')
      ) AS e(table_name, column_name, data_type)
      JOIN information_schema.columns c
        ON c.table_schema = current_schema() AND c.table_name = e.table_name AND c.column_name = e.column_name
     WHERE c.data_type <> e.data_type;
    IF mismatch IS NOT NULL THEN
        RAISE EXCEPTION 'Migración Fase 5 abortada por tipos incompatibles: %', mismatch;
    END IF;
END $$;

-- Numeración comercial -------------------------------------------------
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS commercial_number bigint;

CREATE TABLE IF NOT EXISTS ticket_number_sequences (
    negocio_id bigint,
    next_number bigint,
    version bigint
);
ALTER TABLE ticket_number_sequences ADD COLUMN IF NOT EXISTS negocio_id bigint;
ALTER TABLE ticket_number_sequences ADD COLUMN IF NOT EXISTS next_number bigint;
ALTER TABLE ticket_number_sequences ADD COLUMN IF NOT EXISTS version bigint;

DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM tickets WHERE commercial_number IS NOT NULL AND commercial_number <= 0) THEN
        RAISE EXCEPTION 'Migración Fase 5 abortada: existen commercial_number no positivos';
    END IF;
    IF EXISTS (
        SELECT 1 FROM tickets WHERE commercial_number IS NOT NULL
        GROUP BY negocio_id, commercial_number HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Migración Fase 5 abortada: existen commercial_number duplicados por negocio';
    END IF;
END $$;

-- Solo numera PAID todavía sin número. El orden es determinista y empieza
-- después del máximo ya preservado en cada negocio.
WITH maxima AS (
    SELECT negocio_id, COALESCE(MAX(commercial_number), 0) AS max_number
    FROM tickets GROUP BY negocio_id
), pending AS (
    SELECT t.id,
           COALESCE(m.max_number, 0) + ROW_NUMBER() OVER (
               PARTITION BY t.negocio_id ORDER BY t.paid_at ASC NULLS LAST, t.id ASC
           ) AS assigned_number
    FROM tickets t
    LEFT JOIN maxima m ON m.negocio_id = t.negocio_id
    WHERE t.status = 'PAID' AND t.commercial_number IS NULL
)
UPDATE tickets t SET commercial_number = p.assigned_number
FROM pending p WHERE p.id = t.id;

DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM ticket_number_sequences WHERE negocio_id IS NULL)
       OR EXISTS (SELECT 1 FROM ticket_number_sequences GROUP BY negocio_id HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Migración Fase 5 abortada: secuencias existentes sin negocio o duplicadas';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'ticket_number_sequences'::regclass AND contype = 'p') THEN
        ALTER TABLE ticket_number_sequences ADD CONSTRAINT pk_ticket_number_sequences PRIMARY KEY (negocio_id);
    END IF;
END $$;

INSERT INTO ticket_number_sequences (negocio_id, next_number, version)
SELECT n.id, COALESCE(MAX(t.commercial_number), 0) + 1, 0
FROM negocios n LEFT JOIN tickets t ON t.negocio_id = n.id
GROUP BY n.id
ON CONFLICT (negocio_id) DO UPDATE
SET next_number = GREATEST(ticket_number_sequences.next_number, EXCLUDED.next_number);

UPDATE ticket_number_sequences SET next_number = 1 WHERE next_number IS NULL;
UPDATE ticket_number_sequences SET version = 0 WHERE version IS NULL;
ALTER TABLE ticket_number_sequences ALTER COLUMN negocio_id SET NOT NULL;
ALTER TABLE ticket_number_sequences ALTER COLUMN next_number SET DEFAULT 1;
ALTER TABLE ticket_number_sequences ALTER COLUMN next_number SET NOT NULL;
ALTER TABLE ticket_number_sequences ALTER COLUMN version SET DEFAULT 0;
ALTER TABLE ticket_number_sequences ALTER COLUMN version SET NOT NULL;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'ticket_number_sequences'::regclass AND contype = 'p') THEN
        ALTER TABLE ticket_number_sequences ADD CONSTRAINT pk_ticket_number_sequences PRIMARY KEY (negocio_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_ticket_number_sequences_negocio'
                   AND conrelid = 'ticket_number_sequences'::regclass) THEN
        ALTER TABLE ticket_number_sequences ADD CONSTRAINT fk_ticket_number_sequences_negocio
            FOREIGN KEY (negocio_id) REFERENCES negocios(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_ticket_number_sequences_positive'
                   AND conrelid = 'ticket_number_sequences'::regclass) THEN
        ALTER TABLE ticket_number_sequences ADD CONSTRAINT ck_ticket_number_sequences_positive CHECK (next_number > 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_tickets_commercial_number_positive'
                   AND conrelid = 'tickets'::regclass) THEN
        ALTER TABLE tickets ADD CONSTRAINT ck_tickets_commercial_number_positive
            CHECK (commercial_number IS NULL OR commercial_number > 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_tickets_paid_has_commercial_number'
                   AND conrelid = 'tickets'::regclass) THEN
        ALTER TABLE tickets ADD CONSTRAINT ck_tickets_paid_has_commercial_number
            CHECK (status <> 'PAID' OR commercial_number IS NOT NULL);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_tickets_negocio_commercial_number
    ON tickets (negocio_id, commercial_number) WHERE commercial_number IS NOT NULL;

-- Descuentos por línea -------------------------------------------------
ALTER TABLE ticket_lines ADD COLUMN IF NOT EXISTS subtotal_before_discount numeric(10,2);
ALTER TABLE ticket_lines ADD COLUMN IF NOT EXISTS discount_percentage integer;
ALTER TABLE ticket_lines ADD COLUMN IF NOT EXISTS discount_amount numeric(10,2);
ALTER TABLE ticket_lines ADD COLUMN IF NOT EXISTS discount_applied_by bigint;
ALTER TABLE ticket_lines ADD COLUMN IF NOT EXISTS discount_applied_at timestamp;

-- Históricos existentes se preservan: su subtotal actual se congela como
-- subtotal previo y comienzan declarativamente sin descuento.
UPDATE ticket_lines SET subtotal_before_discount = subtotal WHERE subtotal_before_discount IS NULL;
UPDATE ticket_lines SET discount_percentage = 0 WHERE discount_percentage IS NULL;
UPDATE ticket_lines SET discount_amount = 0.00 WHERE discount_amount IS NULL;
UPDATE ticket_lines SET discount_applied_by = NULL, discount_applied_at = NULL WHERE discount_percentage = 0;

ALTER TABLE ticket_lines ALTER COLUMN subtotal_before_discount SET NOT NULL;
ALTER TABLE ticket_lines ALTER COLUMN discount_percentage SET DEFAULT 0;
ALTER TABLE ticket_lines ALTER COLUMN discount_percentage SET NOT NULL;
ALTER TABLE ticket_lines ALTER COLUMN discount_amount SET DEFAULT 0.00;
ALTER TABLE ticket_lines ALTER COLUMN discount_amount SET NOT NULL;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_ticket_lines_discount_applied_by'
                   AND conrelid = 'ticket_lines'::regclass) THEN
        ALTER TABLE ticket_lines ADD CONSTRAINT fk_ticket_lines_discount_applied_by
            FOREIGN KEY (discount_applied_by) REFERENCES users(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_ticket_lines_discount_percentage'
                   AND conrelid = 'ticket_lines'::regclass) THEN
        ALTER TABLE ticket_lines ADD CONSTRAINT ck_ticket_lines_discount_percentage
            CHECK (discount_percentage IN (0,5,10,15,20,30,35,40,45,50));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_ticket_lines_discount_amounts'
                   AND conrelid = 'ticket_lines'::regclass) THEN
        ALTER TABLE ticket_lines ADD CONSTRAINT ck_ticket_lines_discount_amounts CHECK (
            subtotal_before_discount >= 0 AND discount_amount >= 0
            AND subtotal >= 0 AND discount_amount <= subtotal_before_discount
            AND subtotal = subtotal_before_discount - discount_amount
        );
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_ticket_lines_zero_discount_actor'
                   AND conrelid = 'ticket_lines'::regclass) THEN
        ALTER TABLE ticket_lines ADD CONSTRAINT ck_ticket_lines_zero_discount_actor CHECK (
            discount_percentage <> 0 OR (discount_applied_by IS NULL AND discount_applied_at IS NULL)
        );
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_ticket_lines_discount_applied_by ON ticket_lines (discount_applied_by);
