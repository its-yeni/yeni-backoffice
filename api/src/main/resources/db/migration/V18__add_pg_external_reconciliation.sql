CREATE TABLE pg_settlement_import (
 id BIGINT NOT NULL AUTO_INCREMENT, file_name VARCHAR(120) NOT NULL,
 pg_company VARCHAR(40) NOT NULL, mid VARCHAR(80) NOT NULL, business_date DATE NOT NULL,
 total_count INT NOT NULL, matched_count INT NOT NULL, mismatch_count INT NOT NULL,
 status VARCHAR(40) NOT NULL, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 PRIMARY KEY(id)
);
CREATE TABLE pg_settlement_reconciliation (
 id BIGINT NOT NULL AUTO_INCREMENT, import_id BIGINT NOT NULL, sales_transaction_id BIGINT,
 tid VARCHAR(120), order_no VARCHAR(100), internal_amount DECIMAL(19,2), pg_amount DECIMAL(19,2),
 pg_fee DECIMAL(19,2), status VARCHAR(30) NOT NULL, reason VARCHAR(300), PRIMARY KEY(id)
);
CREATE INDEX idx_pg_reconciliation_import ON pg_settlement_reconciliation(import_id,status);
