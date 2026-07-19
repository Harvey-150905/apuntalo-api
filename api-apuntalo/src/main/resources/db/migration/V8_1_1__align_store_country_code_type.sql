-- F8.1.1: alinea stores.country_code con el mapping JPA String/VARCHAR(2).

-- Preflight ------------------------------------------------------------
DO $$
DECLARE
    current_type text;
    current_length integer;
BEGIN
    IF to_regclass(current_schema() || '.stores') IS NULL THEN
        RAISE EXCEPTION 'V8.1.1 abortada: no existe la tabla stores';
    END IF;

    SELECT data_type, character_maximum_length
      INTO current_type, current_length
      FROM information_schema.columns
     WHERE table_schema = current_schema()
       AND table_name = 'stores'
       AND column_name = 'country_code';

    IF current_type IS NULL THEN
        RAISE EXCEPTION 'V8.1.1 abortada: no existe stores.country_code';
    END IF;
    IF current_type NOT IN ('character', 'character varying') THEN
        RAISE EXCEPTION 'V8.1.1 abortada: stores.country_code tiene tipo incompatible: %', current_type;
    END IF;
    IF current_length IS DISTINCT FROM 2 THEN
        RAISE EXCEPTION 'V8.1.1 abortada: stores.country_code no tiene longitud 2';
    END IF;
    IF EXISTS (
        SELECT 1 FROM stores
         WHERE country_code IS NULL
            OR char_length(btrim(country_code)) <> 2
            OR btrim(country_code) <> upper(btrim(country_code))
    ) THEN
        RAISE EXCEPTION 'V8.1.1 abortada: existen country_code nulos, vacíos, de longitud distinta de 2 o no uppercase';
    END IF;
END $$;

-- Conversión: BTRIM elimina únicamente el padding introducido por CHAR.
ALTER TABLE stores
    ALTER COLUMN country_code TYPE varchar(2)
    USING btrim(country_code)::varchar(2);

-- Postflight -----------------------------------------------------------
DO $$
DECLARE
    resulting_type text;
    resulting_length integer;
    resulting_nullable text;
BEGIN
    SELECT data_type, character_maximum_length, is_nullable
      INTO resulting_type, resulting_length, resulting_nullable
      FROM information_schema.columns
     WHERE table_schema = current_schema()
       AND table_name = 'stores'
       AND column_name = 'country_code';

    IF resulting_type IS DISTINCT FROM 'character varying'
       OR resulting_length IS DISTINCT FROM 2 THEN
        RAISE EXCEPTION 'V8.1.1 postflight: stores.country_code no quedó como VARCHAR(2)';
    END IF;
    IF resulting_nullable IS DISTINCT FROM 'NO' THEN
        RAISE EXCEPTION 'V8.1.1 postflight: stores.country_code dejó de ser NOT NULL';
    END IF;
    IF EXISTS (
        SELECT 1 FROM stores
         WHERE country_code IS NULL
            OR char_length(country_code) <> 2
            OR country_code <> upper(country_code)
    ) THEN
        RAISE EXCEPTION 'V8.1.1 postflight: existen country_code inválidos después de la conversión';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid = 'stores'::regclass
           AND conname = 'ck_stores_country_code_format'
           AND contype = 'c'
    ) THEN
        RAISE EXCEPTION 'V8.1.1 postflight: falta el check ck_stores_country_code_format';
    END IF;
END $$;
