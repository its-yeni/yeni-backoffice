CREATE TABLE inventory_lot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    store_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    lot_no VARCHAR(80) NOT NULL,
    manufactured_date DATE,
    expiration_date DATE NOT NULL,
    received_quantity INT NOT NULL,
    available_quantity INT NOT NULL,
    memo VARCHAR(200),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_inventory_lot_store_variant_lot UNIQUE (store_id, variant_id, lot_no)
);
CREATE INDEX idx_inventory_lot_expiration ON inventory_lot(expiration_date, available_quantity);
CREATE INDEX idx_inventory_lot_variant ON inventory_lot(store_id, variant_id);
