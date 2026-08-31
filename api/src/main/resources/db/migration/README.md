# DB 마이그레이션 — 이력/문서용

이 폴더의 SQL은 **Flyway로 자동 실행되지 않습니다.** Flyway 의존성이 클래스패스에 없고,
로컬·테스트·Fly.io(H2) 환경은 Hibernate `ddl-auto`(`update` / `create-drop`)로 스키마를 반영합니다.

이 파일들의 용도는 **운영 MySQL(`ddl-auto=validate`)에 스키마 변경을 순서대로 수동 반영하기 위한 변경 이력**입니다.

## 알려진 버전 번호 충돌

결제 운영 라인과 커머스(상품/주문/재고) 라인이 별도 브랜치에서 각각 마이그레이션을 추가하면서
아래 번호가 겹쳐 있습니다. 해당 번호는 **두 파일을 모두 적용**하면 되고, 한 번호 안에서의 적용 순서는 무관합니다.

| 버전 | 파일 |
|---|---|
| V6 | `add_followup_worker_fields`, `add_settlement_execution_unique_index` |
| V7 | `add_product_order_domain`, `add_settlement_payout_fields` |
| V8 | `add_inventory_transaction_snapshots`, `add_product_image_and_category` |
| V9 | `add_product_inventory_management`, `extend_audit_log_context` |
| V13 | `add_option_templates`, `add_order_item_variant_snapshot` |

또한 `inventory_transaction` 테이블은 V8(컬럼 추가)과 V15(CREATE TABLE)에서 서로 다른 형태로 기술돼 있습니다.
실제 스키마 기준은 엔티티(`ddl-auto`가 생성하는 형태)이며, 운영 반영 시에는 V15의 `CREATE TABLE` 정의를 기준으로 하고
V8의 `ALTER`는 이미 그 정의에 반영된 것으로 간주하세요.

## Flyway를 정식 도입한다면

1. 위 충돌 번호를 단일 순열로 리넘버(내부 의존 순서 유지: 예 `order_item_variant_snapshot`은 이를 쓰는 V15보다 앞)
2. 현재 운영 스키마를 `flyway baseline` 으로 잡고 이후 변경만 신규 버전으로 관리
3. `ddl-auto` 를 `validate` 로 고정
