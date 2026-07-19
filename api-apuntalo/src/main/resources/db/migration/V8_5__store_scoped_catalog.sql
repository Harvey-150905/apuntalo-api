-- F8.5: subcategories es la categoría persistida real; Category es un enum.
DO $$ BEGIN
 IF to_regclass(current_schema()||'.subcategories') IS NULL OR to_regclass(current_schema()||'.products') IS NULL
   OR to_regclass(current_schema()||'.ticket_lines') IS NULL THEN RAISE EXCEPTION 'V8.5: faltan tablas de catálogo'; END IF;
 IF EXISTS(SELECT 1 FROM subcategories s LEFT JOIN negocios n ON n.id=s.negocio_id WHERE n.id IS NULL)
   OR EXISTS(SELECT 1 FROM products p LEFT JOIN subcategories s ON s.id=p.subcategory_id
     WHERE s.id IS NULL OR s.negocio_id<>p.negocio_id) THEN RAISE EXCEPTION 'V8.5: catálogo huérfano o tenant-crossed'; END IF;
 IF EXISTS(SELECT 1 FROM tickets t JOIN stores s ON s.id=t.store_id WHERE NOT s.primary_store) THEN
   RAISE EXCEPTION 'V8.5: los tickets históricos no están en Principal'; END IF;
END $$;
CREATE TEMP TABLE v8_5_counts ON COMMIT DROP AS SELECT
 (SELECT count(*) FROM subcategories) sc,(SELECT count(*) FROM products) pc,(SELECT count(*) FROM ticket_lines) lc;
CREATE TEMP TABLE v8_5_prices ON COMMIT DROP AS SELECT id,price,name,activo,image_url,image_public_id FROM products;
ALTER TABLE subcategories ADD COLUMN store_id bigint;
ALTER TABLE products ADD COLUMN store_id bigint;
ALTER TABLE ticket_lines ADD COLUMN negocio_id bigint;
ALTER TABLE ticket_lines ADD COLUMN store_id bigint;
UPDATE subcategories x SET store_id=s.id FROM stores s WHERE s.negocio_id=x.negocio_id AND s.primary_store;
UPDATE products x SET store_id=s.id FROM stores s WHERE s.negocio_id=x.negocio_id AND s.primary_store;
UPDATE ticket_lines l SET negocio_id=t.negocio_id,store_id=t.store_id FROM tickets t WHERE t.id=l.ticket_id;
ALTER TABLE subcategories ALTER COLUMN store_id SET NOT NULL;
ALTER TABLE products ALTER COLUMN store_id SET NOT NULL;
ALTER TABLE ticket_lines ALTER COLUMN negocio_id SET NOT NULL;
ALTER TABLE ticket_lines ALTER COLUMN store_id SET NOT NULL;
CREATE UNIQUE INDEX uk_subcategories_id_tenant_store ON subcategories(id,negocio_id,store_id);
CREATE UNIQUE INDEX uk_products_id_tenant_store ON products(id,negocio_id,store_id);
ALTER TABLE subcategories ADD CONSTRAINT fk_subcategories_store_tenant FOREIGN KEY(store_id,negocio_id) REFERENCES stores(id,negocio_id);
ALTER TABLE products ADD CONSTRAINT fk_products_store_tenant FOREIGN KEY(store_id,negocio_id) REFERENCES stores(id,negocio_id);
ALTER TABLE products ADD CONSTRAINT fk_products_subcategory_store FOREIGN KEY(subcategory_id,negocio_id,store_id) REFERENCES subcategories(id,negocio_id,store_id);
ALTER TABLE ticket_lines ADD CONSTRAINT fk_ticket_lines_ticket_store FOREIGN KEY(ticket_id,negocio_id,store_id) REFERENCES tickets(id,negocio_id,store_id);
ALTER TABLE ticket_lines ADD CONSTRAINT fk_ticket_lines_product_store FOREIGN KEY(product_id,negocio_id,store_id) REFERENCES products(id,negocio_id,store_id);
DO $$ DECLARE c text; BEGIN
 FOR c IN SELECT p.conname FROM pg_constraint p WHERE p.conrelid='subcategories'::regclass AND p.contype='u'
   AND (SELECT array_agg(a.attname ORDER BY k.ord) FROM unnest(p.conkey) WITH ORDINALITY k(attnum,ord)
        JOIN pg_attribute a ON a.attrelid=p.conrelid AND a.attnum=k.attnum)
       =ARRAY['negocio_id','nombre','category']::name[]
 LOOP EXECUTE format('ALTER TABLE subcategories DROP CONSTRAINT %I',c); END LOOP;
 FOR c IN SELECT p.conname FROM pg_constraint p WHERE p.conrelid='products'::regclass AND p.contype='u'
   AND (SELECT array_agg(a.attname ORDER BY k.ord) FROM unnest(p.conkey) WITH ORDINALITY k(attnum,ord)
        JOIN pg_attribute a ON a.attrelid=p.conrelid AND a.attnum=k.attnum)
       =ARRAY['negocio_id','name']::name[]
 LOOP EXECUTE format('ALTER TABLE products DROP CONSTRAINT %I',c); END LOOP;
END $$;
ALTER TABLE subcategories ADD CONSTRAINT uk_subcategories_tenant_store_name_category UNIQUE(negocio_id,store_id,nombre,category);
ALTER TABLE products ADD CONSTRAINT uk_products_tenant_store_name UNIQUE(negocio_id,store_id,name);
CREATE INDEX idx_subcategories_store_name ON subcategories(negocio_id,store_id,nombre,id);
CREATE INDEX idx_products_store_active_name ON products(negocio_id,store_id,activo,name,id);
CREATE INDEX idx_products_store_category_active ON products(negocio_id,store_id,subcategory_id,activo,name,id);
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM products p JOIN subcategories s ON s.id=p.subcategory_id WHERE p.store_id<>s.store_id OR p.negocio_id<>s.negocio_id)
 OR EXISTS(SELECT 1 FROM ticket_lines l JOIN products p ON p.id=l.product_id WHERE l.store_id<>p.store_id OR l.negocio_id<>p.negocio_id)
 OR EXISTS(SELECT 1 FROM ticket_lines l JOIN tickets t ON t.id=l.ticket_id WHERE l.store_id<>t.store_id OR l.negocio_id<>t.negocio_id)
 THEN RAISE EXCEPTION 'V8.5 postflight: cruce de Store'; END IF;
 IF (SELECT count(*) FROM subcategories)<>(SELECT sc FROM v8_5_counts) OR (SELECT count(*) FROM products)<>(SELECT pc FROM v8_5_counts)
 OR (SELECT count(*) FROM ticket_lines)<>(SELECT lc FROM v8_5_counts) THEN RAISE EXCEPTION 'V8.5 postflight: conteos alterados'; END IF;
 IF EXISTS(SELECT 1 FROM products p JOIN v8_5_prices b USING(id) WHERE p.price IS DISTINCT FROM b.price OR p.name IS DISTINCT FROM b.name OR p.activo IS DISTINCT FROM b.activo OR p.image_url IS DISTINCT FROM b.image_url OR p.image_public_id IS DISTINCT FROM b.image_public_id) THEN RAISE EXCEPTION 'V8.5 postflight: producto alterado'; END IF;
END $$;
