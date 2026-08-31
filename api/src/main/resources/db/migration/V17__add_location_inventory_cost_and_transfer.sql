ALTER TABLE store_variant_inventory ADD COLUMN reserved_quantity INT NOT NULL DEFAULT 0;
ALTER TABLE store_variant_inventory ADD COLUMN average_unit_cost DECIMAL(19,2) NOT NULL DEFAULT 0;
ALTER TABLE commerce_order_item ADD COLUMN unit_cost DECIMAL(19,2) NULL;
ALTER TABLE commerce_order_item ADD COLUMN cost_of_goods_sold DECIMAL(19,2) NULL;

CREATE TABLE stock_transfer (
 id BIGINT NOT NULL AUTO_INCREMENT,
 transfer_no VARCHAR(60) NOT NULL,
 source_store_id BIGINT NOT NULL,
 destination_store_id BIGINT NOT NULL,
 status VARCHAR(20) NOT NULL,
 reason VARCHAR(200), actor VARCHAR(60),
 shipped_at DATETIME(6), received_at DATETIME(6),
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 PRIMARY KEY(id), CONSTRAINT uk_stock_transfer_no UNIQUE(transfer_no)
);
CREATE TABLE stock_transfer_item (
 id BIGINT NOT NULL AUTO_INCREMENT,
 transfer_id BIGINT NOT NULL, variant_id BIGINT NOT NULL, quantity INT NOT NULL,
 unit_cost DECIMAL(19,2) NOT NULL,
 PRIMARY KEY(id)
);
CREATE INDEX idx_stock_transfer_status ON stock_transfer(status, created_at);
CREATE INDEX idx_stock_transfer_item_transfer ON stock_transfer_item(transfer_id);
