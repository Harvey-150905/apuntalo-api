-- F6.4: reconciliación, sesión de origen del ticket y sesión de cobro.
ALTER TABLE negocios RENAME COLUMN cash_management_enabled TO cash_reconciliation_enabled;

ALTER TABLE cash_sessions ADD COLUMN reconciliation_required boolean;
UPDATE cash_sessions cs
SET reconciliation_required = n.cash_reconciliation_enabled
FROM negocios n
WHERE n.id = cs.negocio_id;
ALTER TABLE cash_sessions ALTER COLUMN reconciliation_required SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_cash_sessions_id_negocio
    ON cash_sessions (id, negocio_id);

ALTER TABLE tickets
    ADD COLUMN origin_cash_session_id bigint,
    ADD COLUMN origin_session_legacy boolean NOT NULL DEFAULT false;
UPDATE tickets SET origin_session_legacy = true;
ALTER TABLE tickets ADD CONSTRAINT fk_tickets_origin_session_tenant
    FOREIGN KEY (origin_cash_session_id, negocio_id)
    REFERENCES cash_sessions (id, negocio_id);
ALTER TABLE tickets ADD CONSTRAINT ck_tickets_origin_session_required
    CHECK (origin_cash_session_id IS NOT NULL OR origin_session_legacy = true);

ALTER TABLE payments
    ADD COLUMN cash_session_id bigint,
    ADD COLUMN session_legacy boolean NOT NULL DEFAULT false;
UPDATE payments SET session_legacy = true WHERE cash_session_id IS NULL;
ALTER TABLE payments ADD CONSTRAINT fk_payments_cash_session_tenant
    FOREIGN KEY (cash_session_id, negocio_id)
    REFERENCES cash_sessions (id, negocio_id);
ALTER TABLE payments ADD CONSTRAINT ck_payments_cash_session_required
    CHECK (cash_session_id IS NOT NULL OR session_legacy = true);

CREATE INDEX idx_tickets_origin_session_status
    ON tickets (negocio_id, origin_cash_session_id, status);
CREATE INDEX idx_payments_cash_session_method
    ON payments (negocio_id, cash_session_id, method);
CREATE INDEX idx_payments_cash_session_ticket
    ON payments (negocio_id, cash_session_id, ticket_id);
