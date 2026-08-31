-- 문서용 스크립트입니다. 실제 스키마는 spring.jpa.hibernate.ddl-auto=update 로 엔티티에서 생성됩니다.
CREATE TABLE supplier (
    id BIGINT NOT NULL AUTO_INCREMENT,
    supplier_code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    manager_name VARCHAR(60),
    contact VARCHAR(60),
    lead_time_days INT NOT NULL DEFAULT 14,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    memo VARCHAR(300),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_supplier_code UNIQUE (supplier_code)
);

CREATE TABLE purchase_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    po_no VARCHAR(40) NOT NULL,
    supplier_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    expected_arrival_date DATE,
    ordered_at TIMESTAMP,
    total_amount DECIMAL(19, 2) NOT NULL DEFAULT 0,
    memo VARCHAR(300),
    actor VARCHAR(40),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_purchase_order_no UNIQUE (po_no)
);

CREATE TABLE purchase_order_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    purchase_order_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    ordered_quantity INT NOT NULL,
    received_quantity INT NOT NULL DEFAULT 0,
    unit_cost DECIMAL(19, 2) NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_purchase_order_status ON purchase_order(status, id);
CREATE INDEX idx_purchase_order_item_po ON purchase_order_item(purchase_order_id);
