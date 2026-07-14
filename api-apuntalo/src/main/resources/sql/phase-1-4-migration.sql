-- =====================================================================
-- Apuntalo API - Migración manual Fases 1-4 (PostgreSQL)
-- =====================================================================
-- Objetivo: consolidar el esquema requerido por las Fases 1-4.
-- Motor: PostgreSQL 12 o superior. Requiere permisos ALTER/CREATE INDEX.
-- Antes de ejecutar: realizar backup y probar en una copia restaurable.
-- Ejecución: psql "$DB_URL" -v ON_ERROR_STOP=1 -f phase-1-4-migration.sql
-- Validación: revisar las consultas de solo lectura situadas al final.
-- El código usa ddl-auto=validate: esta migración debe aplicarse antes del despliegue.
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- 1. USERS: username único globalmente (Fase 1)
-- ---------------------------------------------------------------------

-- 1.1 Abortar antes de modificar datos si la normalización colisionaría.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM users GROUP BY lower(trim(username)) HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Migración abortada: usernames duplicados tras lower(trim()). Ejecute la consulta de diagnóstico del final.';
    END IF;
END $$;

-- 1.2 Normalizar usernames existentes (trim + minúsculas)
UPDATE users
SET username = lower(trim(username))
WHERE username <> lower(trim(username));

-- 1.3 Eliminar el constraint único antiguo (negocio_id, username),
--     cuyo nombre generado por Hibernate no es predecible.
DO $$
DECLARE
    constraint_name text;
BEGIN
    SELECT con.conname INTO constraint_name
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    JOIN LATERAL (
        SELECT array_agg(att.attname ORDER BY keys.ordinality) AS columns
        FROM unnest(con.conkey) WITH ORDINALITY AS keys(attnum, ordinality)
        JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = keys.attnum
    ) cols ON true
    WHERE rel.relname = 'users'
      AND con.contype = 'u'
      AND cols.columns = ARRAY['negocio_id', 'username']::name[]
    LIMIT 1;

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE users DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;

-- 1.4 Columnas nuevas de seguridad (activo, token_version)
ALTER TABLE users ADD COLUMN IF NOT EXISTS activo boolean;
ALTER TABLE users ADD COLUMN IF NOT EXISTS token_version integer;

UPDATE users SET activo = true WHERE activo IS NULL;
UPDATE users SET token_version = 1 WHERE token_version IS NULL;

ALTER TABLE users ALTER COLUMN activo SET DEFAULT true;
ALTER TABLE users ALTER COLUMN activo SET NOT NULL;
ALTER TABLE users ALTER COLUMN token_version SET DEFAULT 1;
ALTER TABLE users ALTER COLUMN token_version SET NOT NULL;

-- 1.5 Constraint único global de username (falla aquí si el diagnóstico
--     1.1 detectó duplicados sin resolver).
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_username_ci ON users (lower(username));

-- ---------------------------------------------------------------------
-- 2. TICKETS: @Version, auditoría de cancelación (Fase 2 / 3)
-- ---------------------------------------------------------------------

ALTER TABLE tickets ADD COLUMN IF NOT EXISTS version bigint;
UPDATE tickets SET version = 0 WHERE version IS NULL;
ALTER TABLE tickets ALTER COLUMN version SET DEFAULT 0;
ALTER TABLE tickets ALTER COLUMN version SET NOT NULL;

ALTER TABLE tickets ADD COLUMN IF NOT EXISTS cancelled_at timestamp;
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS cancelled_by bigint;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_tickets_cancelled_by') THEN
        ALTER TABLE tickets ADD CONSTRAINT fk_tickets_cancelled_by
            FOREIGN KEY (cancelled_by) REFERENCES users(id);
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- 3. MESAS: @Version (Fase 3)
-- ---------------------------------------------------------------------

ALTER TABLE mesas ADD COLUMN IF NOT EXISTS version bigint;
UPDATE mesas SET version = 0 WHERE version IS NULL;
ALTER TABLE mesas ALTER COLUMN version SET DEFAULT 0;
ALTER TABLE mesas ALTER COLUMN version SET NOT NULL;

-- ---------------------------------------------------------------------
-- 4. Un único ticket OPEN por mesa (Fase 3.5)
-- ---------------------------------------------------------------------
-- Índice único parcial: PostgreSQL soporta condiciones WHERE en índices
-- únicos, lo que permite expresar "a lo sumo un ticket OPEN por mesa"
-- directamente en el motor, como defensa adicional al bloqueo pesimista
-- de aplicación (TicketService.create / changeMesa).
CREATE UNIQUE INDEX IF NOT EXISTS uk_ticket_mesa_open
    ON tickets (mesa_id)
    WHERE status = 'OPEN';

-- ---------------------------------------------------------------------
-- 5. IDEMPOTENCY_RECORDS (Fase 4)
-- ---------------------------------------------------------------------
-- Hibernate (ddl-auto=update) crea esta tabla automáticamente al
-- arrancar si no existe; se incluye aquí en forma explícita por si el
-- entorno de destino no usa ddl-auto=update.
CREATE TABLE IF NOT EXISTS idempotency_records (
    id              bigserial PRIMARY KEY,
    tenant_id       bigint       NOT NULL,
    user_id         bigint       NOT NULL,
    idempotency_key varchar(100) NOT NULL,
    operation       varchar(60)  NOT NULL,
    request_hash    varchar(64)  NOT NULL,
    resource_type   varchar(60),
    resource_id     bigint,
    response_status integer,
    response_body   text,
    status          varchar(20)  NOT NULL,
    created_at      timestamp    NOT NULL,
    completed_at    timestamp,
    expires_at      timestamp    NOT NULL,
    CONSTRAINT uk_idempotency_scope UNIQUE (tenant_id, user_id, operation, idempotency_key),
    CONSTRAINT fk_idempotency_tenant FOREIGN KEY (tenant_id) REFERENCES negocios(id),
    CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_idempotency_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_idempotency_expires_at ON idempotency_records (expires_at);

COMMIT;

-- Verificación (solo lectura)
SELECT lower(trim(username)) AS username_normalizado, count(*)
FROM users GROUP BY lower(trim(username)) HAVING count(*) > 1;
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_name IN ('users', 'tickets', 'mesas', 'idempotency_records')
ORDER BY table_name, ordinal_position;
SELECT indexname, indexdef FROM pg_indexes
WHERE tablename IN ('users', 'tickets', 'idempotency_records') ORDER BY tablename, indexname;

-- ---------------------------------------------------------------------
-- Limpieza periódica opcional de registros de idempotencia expirados.
-- El proyecto no tiene scheduler habilitado; se puede invocar a mano,
-- desde un cron externo, o llamar a
-- IdempotencyRecordService.deleteExpired() si en el futuro se habilita
-- @EnableScheduling.
-- ---------------------------------------------------------------------
-- DELETE FROM idempotency_records WHERE expires_at < now();
