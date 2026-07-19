-- F8.2: accesos usuario-tienda y tienda predeterminada.
-- JWT y contexto operativo de Store permanecen fuera de esta migración.

-- Preflight ------------------------------------------------------------
DO $$
DECLARE
    users_tenant_unique boolean;
    stores_tenant_unique boolean;
BEGIN
    IF to_regclass(current_schema() || '.users') IS NULL THEN
        RAISE EXCEPTION 'V8.2 abortada: no existe la tabla users';
    END IF;
    IF to_regclass(current_schema() || '.stores') IS NULL THEN
        RAISE EXCEPTION 'V8.2 abortada: no existe la tabla stores';
    END IF;
    IF to_regclass(current_schema() || '.user_store_access') IS NOT NULL THEN
        RAISE EXCEPTION 'V8.2 abortada: ya existe user_store_access; estado parcial no soportado';
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'users'
          AND column_name = 'default_store_id'
    ) THEN
        RAISE EXCEPTION 'V8.2 abortada: users.default_store_id ya existe; estado parcial no soportado';
    END IF;
    IF EXISTS (SELECT 1 FROM users WHERE id IS NULL OR negocio_id IS NULL) THEN
        RAISE EXCEPTION 'V8.2 abortada: existe un usuario sin id o negocio_id';
    END IF;
    IF EXISTS (
        SELECT 1 FROM users u LEFT JOIN negocios n ON n.id = u.negocio_id WHERE n.id IS NULL
    ) THEN
        RAISE EXCEPTION 'V8.2 abortada: existe un usuario asociado a un negocio inexistente';
    END IF;
    IF EXISTS (
        SELECT user_tenants.negocio_id
        FROM (SELECT DISTINCT negocio_id FROM users) user_tenants
        LEFT JOIN stores s
          ON s.negocio_id = user_tenants.negocio_id AND s.primary_store = true
        GROUP BY user_tenants.negocio_id
        HAVING count(s.id) <> 1
    ) THEN
        RAISE EXCEPTION 'V8.2 abortada: un negocio con usuarios no tiene exactamente una Store Principal';
    END IF;
    IF EXISTS (
        SELECT negocio_id FROM stores WHERE primary_store = true
        GROUP BY negocio_id HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'V8.2 abortada: existen Stores principales duplicadas';
    END IF;

    SELECT EXISTS (
        SELECT 1 FROM pg_index i
        JOIN pg_class t ON t.oid = i.indrelid
        JOIN pg_namespace ns ON ns.oid = t.relnamespace
        WHERE ns.nspname = current_schema() AND t.relname = 'users' AND i.indisunique
          AND (SELECT array_agg(a.attname ORDER BY k.ordinality)
               FROM unnest(i.indkey) WITH ORDINALITY k(attnum, ordinality)
               JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = k.attnum)
              = ARRAY['id', 'negocio_id']::name[]
    ) INTO users_tenant_unique;
    SELECT EXISTS (
        SELECT 1 FROM pg_index i
        JOIN pg_class t ON t.oid = i.indrelid
        JOIN pg_namespace ns ON ns.oid = t.relnamespace
        WHERE ns.nspname = current_schema() AND t.relname = 'stores' AND i.indisunique
          AND (SELECT array_agg(a.attname ORDER BY k.ordinality)
               FROM unnest(i.indkey) WITH ORDINALITY k(attnum, ordinality)
               JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = k.attnum)
              = ARRAY['id', 'negocio_id']::name[]
    ) INTO stores_tenant_unique;
    IF NOT users_tenant_unique THEN
        RAISE EXCEPTION 'V8.2 abortada: falta UNIQUE compatible en users(id, negocio_id)';
    END IF;
    IF NOT stores_tenant_unique THEN
        RAISE EXCEPTION 'V8.2 abortada: falta UNIQUE compatible en stores(id, negocio_id)';
    END IF;
END $$;

CREATE TEMP TABLE v8_2_users_snapshot ON COMMIT DROP AS
SELECT id, username, password, role, activo, token_version, negocio_id FROM users;

CREATE TEMP TABLE v8_2_stores_snapshot ON COMMIT DROP AS
SELECT id, to_jsonb(s) AS payload FROM stores s;

-- Estructura -----------------------------------------------------------
CREATE TABLE user_store_access (
    user_id bigint NOT NULL,
    store_id bigint NOT NULL,
    negocio_id bigint NOT NULL,
    active boolean NOT NULL DEFAULT true,
    assigned_at timestamp without time zone NOT NULL,
    assigned_by bigint,

    CONSTRAINT pk_user_store_access PRIMARY KEY (user_id, store_id),
    CONSTRAINT uk_user_store_access_tenant_user_store UNIQUE (negocio_id, user_id, store_id),
    CONSTRAINT fk_user_store_access_user_tenant FOREIGN KEY (user_id, negocio_id)
        REFERENCES users(id, negocio_id),
    CONSTRAINT fk_user_store_access_store_tenant FOREIGN KEY (store_id, negocio_id)
        REFERENCES stores(id, negocio_id),
    CONSTRAINT fk_user_store_access_assigned_by_tenant FOREIGN KEY (assigned_by, negocio_id)
        REFERENCES users(id, negocio_id)
);

CREATE INDEX idx_user_store_access_tenant_store_active_user
    ON user_store_access (negocio_id, store_id, active, user_id);
CREATE INDEX idx_user_store_access_tenant_user_active_store
    ON user_store_access (negocio_id, user_id, active, store_id);

ALTER TABLE users ADD COLUMN default_store_id bigint;

-- Backfill: una asignación SYSTEM (assigned_by null) a Principal por usuario.
INSERT INTO user_store_access (user_id, store_id, negocio_id, active, assigned_at, assigned_by)
SELECT u.id, s.id, u.negocio_id, true, LOCALTIMESTAMP, NULL
FROM users u
JOIN stores s ON s.negocio_id = u.negocio_id AND s.primary_store = true;

UPDATE users u
SET default_store_id = s.id
FROM stores s
WHERE s.negocio_id = u.negocio_id AND s.primary_store = true;

-- Postflight inicial ---------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM users WHERE default_store_id IS NULL) THEN
        RAISE EXCEPTION 'V8.2 postflight inicial: existe un usuario sin default Store';
    END IF;
    IF EXISTS (
        SELECT u.id FROM users u
        JOIN stores s ON s.negocio_id = u.negocio_id AND s.primary_store = true
        LEFT JOIN user_store_access a ON a.user_id = u.id AND a.store_id = s.id
        GROUP BY u.id HAVING count(a.store_id) <> 1
    ) THEN
        RAISE EXCEPTION 'V8.2 postflight inicial: un usuario no tiene exactamente un acceso a Principal';
    END IF;
END $$;

ALTER TABLE users ALTER COLUMN default_store_id SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT fk_users_default_store_tenant
    FOREIGN KEY (default_store_id, negocio_id) REFERENCES stores(id, negocio_id);
ALTER TABLE users ADD CONSTRAINT fk_users_default_store_access
    FOREIGN KEY (id, default_store_id) REFERENCES user_store_access(user_id, store_id)
    DEFERRABLE INITIALLY DEFERRED;

-- Postflight final -----------------------------------------------------
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM user_store_access a
        JOIN users u ON u.id = a.user_id
        JOIN stores s ON s.id = a.store_id
        WHERE a.negocio_id <> u.negocio_id OR a.negocio_id <> s.negocio_id
    ) THEN
        RAISE EXCEPTION 'V8.2 postflight: existe un acceso entre tenants distintos';
    END IF;
    IF EXISTS (
        SELECT 1 FROM users u
        JOIN stores s ON s.id = u.default_store_id
        LEFT JOIN user_store_access a
          ON a.user_id = u.id AND a.store_id = u.default_store_id AND a.negocio_id = u.negocio_id
        WHERE s.negocio_id <> u.negocio_id OR a.user_id IS NULL OR a.active IS DISTINCT FROM true
    ) THEN
        RAISE EXCEPTION 'V8.2 postflight: existe un default Store sin acceso activo tenant-safe';
    END IF;
    IF EXISTS (
        SELECT 1 FROM users u WHERE NOT EXISTS (
            SELECT 1 FROM user_store_access a
            WHERE a.user_id = u.id AND a.negocio_id = u.negocio_id AND a.active = true
        )
    ) THEN
        RAISE EXCEPTION 'V8.2 postflight: existe un usuario sin acceso activo';
    END IF;
    IF EXISTS (
        SELECT 1 FROM user_store_access a
        LEFT JOIN users u ON u.id = a.user_id AND u.negocio_id = a.negocio_id
        LEFT JOIN stores s ON s.id = a.store_id AND s.negocio_id = a.negocio_id
        LEFT JOIN users actor ON actor.id = a.assigned_by AND actor.negocio_id = a.negocio_id
        WHERE u.id IS NULL OR s.id IS NULL OR (a.assigned_by IS NOT NULL AND actor.id IS NULL)
    ) THEN
        RAISE EXCEPTION 'V8.2 postflight: existe un acceso huérfano';
    END IF;
    IF (SELECT count(*) FROM users) <> (SELECT count(*) FROM v8_2_users_snapshot) THEN
        RAISE EXCEPTION 'V8.2 postflight: cambió el conteo de usuarios';
    END IF;
    IF EXISTS (
        SELECT 1 FROM users u FULL JOIN v8_2_users_snapshot b ON b.id = u.id
        WHERE u.id IS NULL OR b.id IS NULL
           OR u.username IS DISTINCT FROM b.username
           OR u.password IS DISTINCT FROM b.password
           OR u.role IS DISTINCT FROM b.role
           OR u.activo IS DISTINCT FROM b.activo
           OR u.token_version IS DISTINCT FROM b.token_version
           OR u.negocio_id IS DISTINCT FROM b.negocio_id
    ) THEN
        RAISE EXCEPTION 'V8.2 postflight: se alteraron datos preexistentes de usuarios';
    END IF;
    IF EXISTS (
        SELECT 1 FROM stores s FULL JOIN v8_2_stores_snapshot b ON b.id = s.id
        WHERE s.id IS NULL OR b.id IS NULL OR to_jsonb(s) IS DISTINCT FROM b.payload
    ) THEN
        RAISE EXCEPTION 'V8.2 postflight: se modificaron Stores';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'users'
          AND column_name = 'default_store_id' AND is_nullable = 'NO'
    ) THEN
        RAISE EXCEPTION 'V8.2 postflight: users.default_store_id no quedó NOT NULL';
    END IF;
END $$;
