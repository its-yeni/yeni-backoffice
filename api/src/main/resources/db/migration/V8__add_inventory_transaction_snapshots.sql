ALTER TABLE inventory_transaction
    ADD COLUMN stock_before INT NOT NULL DEFAULT 0;

ALTER TABLE inventory_transaction
    ADD COLUMN reserved_before INT NOT NULL DEFAULT 0;

UPDATE inventory_transaction
SET stock_before = CASE
        WHEN type IN ('RECEIPT', 'ADJUST_IN') THEN stock_after - quantity
        WHEN type IN ('ADJUST_OUT', 'SHIPMENT') THEN stock_after + quantity
        ELSE stock_after
    END,
    reserved_before = CASE
        WHEN type = 'RESERVE' THEN reserved_after - quantity
        WHEN type IN ('RELEASE', 'SHIPMENT') THEN reserved_after + quantity
        ELSE reserved_after
    END;

CREATE INDEX ix_inventory_transaction_created_type
    ON inventory_transaction (created_at, type);

CREATE INDEX ix_inventory_transaction_reference
    ON inventory_transaction (reference_type, reference_id);
