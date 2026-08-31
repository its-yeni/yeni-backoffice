CREATE TABLE IF NOT EXISTS product (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_code VARCHAR(80) NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    category VARCHAR(100),
    sale_price DECIMAL(19,2) NOT NULL,
    stock_quantity INT NOT NULL,
    sale_status VARCHAR(30) NOT NULL,
    version BIGINT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_product_code UNIQUE (product_code)
);

ALTER TABLE commerce_order ADD COLUMN paid_amount DECIMAL(19,2) NOT NULL DEFAULT 0;
ALTER TABLE commerce_order ADD COLUMN cancelled_amount DECIMAL(19,2) NOT NULL DEFAULT 0;
ALTER TABLE commerce_order ADD COLUMN stock_restored BIT NOT NULL DEFAULT 0;
ALTER TABLE commerce_order_item ADD COLUMN product_id BIGINT NULL;
CREATE INDEX idx_commerce_order_item_product_id ON commerce_order_item(product_id);
