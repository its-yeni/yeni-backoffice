ALTER TABLE pg_settlement_reconciliation ADD COLUMN resolution_status VARCHAR(20) NOT NULL DEFAULT 'OPEN';
ALTER TABLE pg_settlement_reconciliation ADD COLUMN assignee VARCHAR(80);
ALTER TABLE pg_settlement_reconciliation ADD COLUMN resolution_note VARCHAR(500);
ALTER TABLE pg_settlement_reconciliation ADD COLUMN resolved_at TIMESTAMP;
UPDATE pg_settlement_reconciliation SET resolution_status = 'NOT_REQUIRED' WHERE status = 'MATCHED';
CREATE INDEX idx_pg_reconciliation_resolution ON pg_settlement_reconciliation(import_id, resolution_status);
