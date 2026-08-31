ALTER TABLE sales_transaction ADD COLUMN confirmed_yn BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sales_transaction ADD COLUMN confirmed_at TIMESTAMP;
CREATE INDEX idx_sales_confirmation_settlement
    ON sales_transaction(business_date, confirmed_yn, settlement_included_yn, store_id);

ALTER TABLE payment_transaction ADD COLUMN product_name VARCHAR(200);
