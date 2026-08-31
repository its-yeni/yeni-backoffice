ALTER TABLE settlement_statement
    ADD COLUMN adjustment_amount DECIMAL(19,2) NOT NULL DEFAULT 0;

ALTER TABLE settlement_statement
    ADD COLUMN hold_amount DECIMAL(19,2) NOT NULL DEFAULT 0;

ALTER TABLE settlement_statement
    ADD COLUMN scheduled_payout_date DATE NULL;

ALTER TABLE settlement_statement
    ADD COLUMN paid_at DATETIME NULL;

ALTER TABLE settlement_statement
    ADD COLUMN payout_reference VARCHAR(80) NULL;

ALTER TABLE settlement_statement
    ADD COLUMN payout_account_masked VARCHAR(80) NULL;
