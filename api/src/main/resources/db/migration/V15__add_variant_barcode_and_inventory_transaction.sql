-- 참고: 이 프로젝트는 로컬/테스트/fly 환경에서 Hibernate `ddl-auto=update`로 스키마를 자동 반영하고
-- (Flyway는 클래스패스에 없어 자동 실행되지 않음), 이 파일은 기존 V1~V14와 같은 방식으로
-- 운영 DB(ddl-auto=validate)에 수동으로 반영하기 위한 이력/문서용 SQL이다.

ALTER TABLE product_variant ADD COLUMN barcode VARCHAR(64) NULL;
ALTER TABLE product_variant ADD CONSTRAINT uk_product_variant_barcode UNIQUE (barcode);

CREATE TABLE inventory_transaction (
    id BIGINT NOT NULL AUTO_INCREMENT,
    variant_id BIGINT NOT NULL,
    sku VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    quantity INT NOT NULL,
    before_quantity INT NOT NULL,
    after_quantity INT NOT NULL,
    reason VARCHAR(200),
    reference_type VARCHAR(40),
    reference_id BIGINT,
    actor VARCHAR(40),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY(id)
);
CREATE INDEX idx_inventory_transaction_variant ON inventory_transaction(variant_id);
CREATE INDEX idx_inventory_transaction_reference ON inventory_transaction(reference_type, reference_id);
