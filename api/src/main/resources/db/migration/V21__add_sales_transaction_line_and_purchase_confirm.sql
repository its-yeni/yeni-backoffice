-- 참고: 이 프로젝트는 로컬/테스트/fly 환경에서 Hibernate ddl-auto로 스키마를 반영하고,
-- 이 파일은 운영 DB(ddl-auto=validate) 수동 반영을 위한 이력/문서용 SQL이다.

-- 1) 배송 완료 후 구매 확정 시각 (매출이 정산 대상으로 전환된 시점)
ALTER TABLE commerce_order ADD COLUMN purchase_confirmed_at TIMESTAMP;

-- 2) 매출 명세 라인 — 매출 헤더(sales_transaction, 결제/취소 단위)를 상품 단위로 분해.
--    주문 시점 분류명을 snapshot으로 남겨 상품별/분류별 매출·정산 집계를 가능하게 한다.
--    라인 lineAmount 합계 = 헤더 saleAmount (마지막 라인이 반올림 잔액을 흡수).
CREATE TABLE sales_transaction_line (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sales_transaction_id BIGINT NOT NULL,
    store_id BIGINT,
    order_id BIGINT,
    order_item_id BIGINT,
    product_id BIGINT,
    product_name VARCHAR(200),
    category_name VARCHAR(100) NOT NULL,
    sku VARCHAR(100),
    quantity INT NOT NULL,
    sale_type VARCHAR(20) NOT NULL,
    line_amount DECIMAL(19,2) NOT NULL,
    supply_amount DECIMAL(19,2) NOT NULL,
    vat_amount DECIMAL(19,2) NOT NULL,
    business_date DATE NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    confirmed_yn BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_sales_line_header ON sales_transaction_line(sales_transaction_id);
CREATE INDEX idx_sales_line_category ON sales_transaction_line(business_date, category_name);
CREATE INDEX idx_sales_line_product ON sales_transaction_line(product_id);

-- 3) 주문 상품에 주문 시점 분류명 snapshot
ALTER TABLE commerce_order_item ADD COLUMN category_name VARCHAR(100);

-- 4) OrderStatus 에 PURCHASE_CONFIRMED 값 추가(문자열 ENUM 컬럼이라 스키마 변경 없음). 기존 CANCELED(오타) 값은 미사용으로 제거됨.
