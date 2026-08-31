-- 참고: 이 프로젝트는 로컬/테스트/fly 환경에서 Hibernate `ddl-auto=update`로 스키마를 자동 반영하고
-- (Flyway는 클래스패스에 없어 자동 실행되지 않음), 이 파일은 기존 V1~V15와 같은 방식으로
-- 운영 DB(ddl-auto=validate)에 수동으로 반영하기 위한 이력/문서용 SQL이다.

-- 배송(CommerceDelivery): 주문 1건이 여러 배송(부분배송)을 가질 수 있어 orderId에 유니크 제약을 두지 않는다.
CREATE TABLE commerce_delivery (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    receiver_name VARCHAR(60) NOT NULL,
    receiver_phone VARCHAR(30) NOT NULL,
    zip_code VARCHAR(10),
    address1 VARCHAR(200) NOT NULL,
    address2 VARCHAR(100),
    delivery_request VARCHAR(200),
    carrier VARCHAR(40),
    tracking_number VARCHAR(60),
    status VARCHAR(20) NOT NULL,
    shipped_at DATETIME(6),
    delivered_at DATETIME(6),
    return_reason VARCHAR(200),
    returned_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY(id)
);
CREATE INDEX idx_commerce_delivery_order ON commerce_delivery(order_id);

ALTER TABLE commerce_order_item ADD COLUMN delivery_id BIGINT NULL;

-- 반품(CommerceReturn): 접수 → 검수 → 완료(재고 복원·환불 확정) / 반려. 검수 통과 전에는 재고/환불에 반영되지 않는다.
CREATE TABLE commerce_return (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    delivery_id BIGINT,
    reason VARCHAR(500) NOT NULL,
    responsibility VARCHAR(20) NOT NULL,
    return_shipping_fee DECIMAL(19,2) NOT NULL,
    refund_amount DECIMAL(19,2),
    status VARCHAR(20) NOT NULL,
    inspected_at DATETIME(6),
    processed_at DATETIME(6),
    reject_reason VARCHAR(500),
    refund_status VARCHAR(20),
    refund_failure_reason VARCHAR(500),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY(id)
);
CREATE INDEX idx_commerce_return_order ON commerce_return(order_id);
