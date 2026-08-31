-- 문서용 스크립트입니다. 실제 스키마는 spring.jpa.hibernate.ddl-auto=update 로 엔티티에서 생성됩니다.

-- 매장별 안전재고 (기존에는 product_variant.safety_stock 하나뿐이라 매장별 회전율 차이를 못 담았다)
ALTER TABLE store_variant_inventory ADD COLUMN safety_stock INT NOT NULL DEFAULT 0;

-- 재고 이동 시 출발 매장에서 소비한 대표 LOT 스냅샷 (도착 매장 입고 시 유통기한 추적을 잇기 위함)
ALTER TABLE stock_transfer_item ADD COLUMN lot_no VARCHAR(90);
ALTER TABLE stock_transfer_item ADD COLUMN expiration_date DATE;

-- inventory_lot.expiration_date 를 nullable 로 완화 (의류 등 유통기한이 없는 품목도 LOT 추적 대상)
ALTER TABLE inventory_lot ALTER COLUMN expiration_date DROP NOT NULL;
