-- 포트폴리오 데모(fly 배포) 전용 시드 데이터.
-- fly 프로필은 인메모리 H2를 써서 배포/재시작마다 DB가 초기화되므로,
-- 방문자가 빈 화면을 보지 않도록 상품/카테고리/옵션 기본값을 매번 다시 채워 넣는다.
-- (주문/결제/매출원장 데모 데이터는 실제 서비스 로직을 그대로 타야 해서
--  core의 DemoOrderSeedInitializer가 애플리케이션 기동 시 별도로 채운다.)
--
-- 이 스크립트는 새로 생성된 빈 스키마에서만 실행되고(테이블당 첫 번째 insert이므로
-- id는 항상 1부터 순서대로 채번된다), 아래 각 섹션의 FK 값은 그 채번 순서를 그대로 참조한다.

-- 1) 카테고리 (온라인몰 4개 + 외식 매장 3개) → id 1~7
INSERT INTO product_category (store_code, brand_id, category_name, sort_order, exposed, created_at, updated_at) VALUES
    ('YENI-SHOP-01', NULL, '기본', 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-SHOP-01', NULL, '의류', 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-SHOP-01', NULL, '잡화', 3, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-SHOP-01', NULL, '리빙', 4, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-FOOD-01', NULL, '피자', 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-FOOD-01', NULL, '사이드', 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-FOOD-01', NULL, '음료', 3, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 2) 상품 (온라인몰 3개 + 외식 매장 3개) → id 1~6
INSERT INTO product (store_code, brand_id, product_code, product_name, category, image_url, sale_price, stock_quantity, inventory_managed, sale_status, version, created_at, updated_at) VALUES
    ('YENI-SHOP-01', NULL, 'YENI-TEE-001', 'Yeni 시그니처 티셔츠', '의류', NULL, 29000, 30, TRUE, 'ON_SALE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-SHOP-01', NULL, 'YENI-BAG-001', 'Yeni 데일리 토트백', '잡화', NULL, 39000, 20, TRUE, 'ON_SALE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-SHOP-01', NULL, 'YENI-MUG-001', 'Yeni 오피스 머그', '리빙', NULL, 15000, 50, TRUE, 'ON_SALE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-FOOD-01', NULL, 'FOOD-PIZZA-001', 'Yeni 시그니처 피자', '피자', NULL, 23900, 100, TRUE, 'ON_SALE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-FOOD-01', NULL, 'FOOD-SIDE-001', '갈릭 치즈볼', '사이드', NULL, 6900, 80, TRUE, 'ON_SALE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-FOOD-01', NULL, 'FOOD-DRINK-001', '콜라 500ml', '음료', NULL, 2500, 200, TRUE, 'ON_SALE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 3) 옵션 템플릿 (여러 상품에 재사용하는 "사이즈"/"색상") → group id 1~2, value id 1~5
INSERT INTO option_group_template (template_name, selection_type, required_option, min_selection, max_selection, sort_order, created_at, updated_at) VALUES
    ('사이즈', 'SINGLE', TRUE, 1, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('색상', 'SINGLE', FALSE, 0, 1, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO option_value_template (template_group_id, value_name, default_additional_price, sort_order, created_at, updated_at) VALUES
    (1, 'S', 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, 'M', 0, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, 'L', 0, 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '블랙', 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '화이트', 0, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 4) 대표 상품(Yeni 시그니처 티셔츠, product id=1)에 "사이즈" 옵션 적용
--    (관리자 화면의 "템플릿에서 적용" 결과와 동일한 스냅샷 데이터)
INSERT INTO product_option_group (product_id, group_name, selection_type, required_option, min_selection, max_selection, sort_order, exposed, created_at, updated_at) VALUES
    (1, '사이즈', 'SINGLE', TRUE, 1, 1, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO product_option_value (option_group_id, value_name, additional_price, inventory_managed, stock_quantity, sale_status, sort_order, created_at, updated_at) VALUES
    (1, 'S', 0, FALSE, 0, 'ON_SALE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, 'M', 0, FALSE, 0, 'ON_SALE', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, 'L', 0, FALSE, 0, 'ON_SALE', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 5) 옵션 조합(재고 단위). combination_key는 product_option_value.id를 그대로 참조한다
--    (ProductVariantService.generate()가 만드는 값과 동일한 형식).
INSERT INTO product_variant (product_id, sku, combination_key, option_summary, additional_price, stock_quantity, reserved_quantity, safety_stock, sale_status, sort_order, created_at, updated_at) VALUES
    (1, 'YENI-TEE-001-S', '1', 'S', 0, 15, 0, 5, 'ON_SALE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, 'YENI-TEE-001-M', '2', 'M', 0, 20, 0, 5, 'ON_SALE', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, 'YENI-TEE-001-L', '3', 'L', 0, 10, 0, 5, 'ON_SALE', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 6) 목록형 운영 화면이 충분한 밀도로 보이도록 마스터 데이터를 10건 이상으로 보강한다.
INSERT INTO product_category (store_code, brand_id, category_name, sort_order, exposed, created_at, updated_at) VALUES
    ('YENI-SHOP-01', NULL, '문구', 5, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-SHOP-01', NULL, '디지털', 6, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-FOOD-01', NULL, '세트', 4, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO product (store_code, brand_id, product_code, product_name, category, image_url, sale_price, stock_quantity, inventory_managed, sale_status, version, created_at, updated_at) VALUES
    ('YENI-SHOP-01', NULL, 'YENI-NOTE-001', 'Yeni 위클리 플래너', '문구', NULL, 12000, 85, TRUE, 'ON_SALE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-SHOP-01', NULL, 'YENI-PEN-001', 'Yeni 젤 펜 세트', '문구', NULL, 8900, 120, TRUE, 'ON_SALE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-SHOP-01', NULL, 'YENI-STAND-001', '알루미늄 노트북 스탠드', '디지털', NULL, 42000, 18, TRUE, 'ON_SALE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-FOOD-01', NULL, 'FOOD-SET-001', '시그니처 피자 세트', '세트', NULL, 31900, 60, TRUE, 'ON_SALE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO option_group_template (template_name, selection_type, required_option, min_selection, max_selection, sort_order, created_at, updated_at) VALUES
    ('도우 선택', 'SINGLE', TRUE, 1, 1, 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('토핑 추가', 'MULTIPLE', FALSE, 0, 5, 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('선물 포장', 'SINGLE', FALSE, 0, 1, 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('각인 문구', 'SINGLE', FALSE, 0, 1, 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('음료 선택', 'SINGLE', TRUE, 1, 1, 7, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('맵기 선택', 'SINGLE', TRUE, 1, 1, 8, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('배송 방식', 'SINGLE', TRUE, 1, 1, 9, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('보증 기간', 'SINGLE', FALSE, 0, 1, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO option_value_template (template_group_id, value_name, default_additional_price, sort_order, created_at, updated_at) VALUES
    (3, '오리지널 도우', 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '씬 도우', 0, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (4, '치즈 추가', 2000, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (4, '페퍼로니 추가', 2500, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (5, '기본 포장', 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO product_option_group (product_id, group_name, selection_type, required_option, min_selection, max_selection, sort_order, exposed, created_at, updated_at) VALUES
    (2, '색상', 'SINGLE', TRUE, 1, 1, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '포장 선택', 'SINGLE', FALSE, 0, 1, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (4, '도우 선택', 'SINGLE', TRUE, 1, 1, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (4, '토핑 추가', 'MULTIPLE', FALSE, 0, 5, 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (5, '소스 선택', 'SINGLE', FALSE, 0, 1, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (7, '내지 색상', 'SINGLE', TRUE, 1, 1, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (9, '색상', 'SINGLE', TRUE, 1, 1, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (10, '음료 선택', 'SINGLE', TRUE, 1, 1, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (10, '사이드 선택', 'SINGLE', TRUE, 1, 1, 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO product_option_value (option_group_id, value_name, additional_price, inventory_managed, stock_quantity, sale_status, sort_order, created_at, updated_at) VALUES
    (2, '블랙', 0, FALSE, 0, 'ON_SALE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '오프화이트', 0, FALSE, 0, 'ON_SALE', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, '선물 포장', 2000, FALSE, 0, 'ON_SALE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (4, '오리지널', 0, FALSE, 0, 'ON_SALE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (4, '씬', 0, FALSE, 0, 'ON_SALE', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (5, '치즈 추가', 2000, FALSE, 0, 'ON_SALE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (6, '갈릭 디핑', 500, FALSE, 0, 'ON_SALE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (7, '아이보리', 0, FALSE, 0, 'ON_SALE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (8, '실버', 0, FALSE, 0, 'ON_SALE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (9, '콜라', 0, FALSE, 0, 'ON_SALE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (10, '치즈볼', 0, FALSE, 0, 'ON_SALE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO product_variant (product_id, sku, combination_key, option_summary, additional_price, stock_quantity, reserved_quantity, safety_stock, sale_status, sort_order, created_at, updated_at) VALUES
    (2, 'YENI-BAG-001-BK', '4', '블랙', 0, 10, 0, 5, 'ON_SALE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 'YENI-BAG-001-OW', '5', '오프화이트', 0, 10, 0, 5, 'ON_SALE', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (4, 'FOOD-PIZZA-001-OR', '7', '오리지널', 0, 50, 0, 5, 'ON_SALE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (4, 'FOOD-PIZZA-001-TH', '8', '씬', 0, 50, 0, 5, 'ON_SALE', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (7, 'YENI-NOTE-001-IV', '11', '아이보리', 0, 40, 0, 5, 'ON_SALE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (9, 'YENI-STAND-001-SV', '12', '실버', 0, 18, 0, 5, 'ON_SALE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (10, 'FOOD-SET-001-STD', '13,14', '콜라 / 치즈볼', 0, 60, 0, 5, 'ON_SALE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 7) 매장 컨텍스트로 조회해도 카테고리와 상품이 각각 10개씩 보이도록 운영형 샘플을 보강한다.
INSERT INTO product_category (store_code, brand_id, category_name, sort_order, exposed, created_at, updated_at) VALUES
    ('YENI-SHOP-01', NULL, '홈리빙', 7, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-SHOP-01', NULL, '오피스', 8, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-SHOP-01', NULL, '선물', 9, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-SHOP-01', NULL, '시즌', 10, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-FOOD-01', NULL, '파스타', 5, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-FOOD-01', NULL, '샐러드', 6, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-FOOD-01', NULL, '디저트', 7, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-FOOD-01', NULL, '커피', 8, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-FOOD-01', NULL, '브런치', 9, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-FOOD-01', NULL, '키즈', 10, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO product (store_code, brand_id, product_code, product_name, category, image_url, sale_price, stock_quantity, inventory_managed, sale_status, version, created_at, updated_at) VALUES
    ('YENI-SHOP-01', NULL, 'YENI-LAMP-001', 'Yeni 무드 테이블 램프', '홈리빙', NULL, 59000, 14, TRUE, 'ON_SALE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-SHOP-01', NULL, 'YENI-POUCH-001', 'Yeni 데일리 파우치', '잡화', NULL, 19000, 42, TRUE, 'ON_SALE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-SHOP-01', NULL, 'YENI-GIFT-001', 'Yeni 웰컴 기프트 세트', '선물', NULL, 35000, 26, TRUE, 'ON_SALE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-SHOP-01', NULL, 'YENI-KEYRING-001', 'Yeni 로고 키링', '리빙', NULL, 7900, 95, TRUE, 'ON_SALE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-FOOD-01', NULL, 'FOOD-PASTA-001', '트러플 크림 파스타', '파스타', NULL, 16900, 70, TRUE, 'ON_SALE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-FOOD-01', NULL, 'FOOD-SALAD-001', '그릴 치킨 샐러드', '샐러드', NULL, 11900, 55, TRUE, 'ON_SALE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-FOOD-01', NULL, 'FOOD-DESSERT-001', '바스크 치즈 케이크', '디저트', NULL, 6500, 45, TRUE, 'ON_SALE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-FOOD-01', NULL, 'FOOD-COFFEE-001', '콜드브루 라떼', '커피', NULL, 5200, 120, TRUE, 'ON_SALE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-FOOD-01', NULL, 'FOOD-BRUNCH-001', '에그 베네딕트 플레이트', '브런치', NULL, 14900, 35, TRUE, 'ON_SALE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('YENI-FOOD-01', NULL, 'FOOD-KIDS-001', '키즈 미니 피자 세트', '키즈', NULL, 9900, 40, TRUE, 'ON_SALE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
