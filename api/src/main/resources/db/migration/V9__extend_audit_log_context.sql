ALTER TABLE audit_log ADD COLUMN actor VARCHAR(60) NULL;
ALTER TABLE audit_log ADD COLUMN result_status VARCHAR(30) NULL;
ALTER TABLE audit_log ADD COLUMN request_id VARCHAR(80) NULL;
ALTER TABLE audit_log ADD COLUMN before_value TEXT NULL;
ALTER TABLE audit_log ADD COLUMN after_value TEXT NULL;

UPDATE audit_log SET actor = 'SYSTEM' WHERE actor IS NULL;
UPDATE audit_log SET result_status = 'SUCCESS' WHERE result_status IS NULL;

CREATE INDEX ix_audit_log_logged_domain ON audit_log (logged_at, domain_type);
CREATE INDEX ix_audit_log_request_id ON audit_log (request_id);
