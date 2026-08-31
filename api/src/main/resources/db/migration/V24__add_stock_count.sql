-- 문서용 스크립트입니다. 실제 스키마는 spring.jpa.hibernate.ddl-auto=update 로 엔티티에서 생성됩니다.
CREATE TABLE stock_count (
    id BIGINT NOT NULL AUTO_INCREMENT,
    count_no VARCHAR(40) NOT NULL,
    store_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    memo VARCHAR(300),
    actor VARCHAR(40),
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_stock_count_no UNIQUE (count_no)
);

CREATE TABLE stock_count_line (
    id BIGINT NOT NULL AUTO_INCREMENT,
    stock_count_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    system_quantity INT NOT NULL,
    counted_quantity INT,
    difference_quantity INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_stock_count_line UNIQUE (stock_count_id, variant_id)
);
CREATE INDEX idx_stock_count_line_count ON stock_count_line(stock_count_id);
