-- F9.7: subcategories obtiene bandera de activación para reemplazar el
-- borrado físico (alineado con products.activo). No modifica migraciones V8.
ALTER TABLE subcategories ADD COLUMN activo boolean NOT NULL DEFAULT true;
CREATE INDEX idx_subcategories_store_active_name ON subcategories(negocio_id, store_id, activo, nombre, id);
