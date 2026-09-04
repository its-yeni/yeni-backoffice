# Yeni Backoffice

커머스 운영 백오피스입니다. 주문·결제·매출·정산·재고를 각각 별도 상태로 추적하고,
화면마다 "지금 확인할 예외"와 "다음 작업"을 함께 보여주는 데 초점을 뒀습니다.

실무에서 다뤄온 커머스 운영·결제·정산 업무를 Spring 스택으로 다시 설계하면서,
개념으로만 알던 부분과 개선하고 싶었던 흐름을 직접 구현해 채워가는 프로젝트입니다.

- Demo: https://yeni-demo.fly.dev/
- Repository: https://github.com/Yeni924/yeni-backoffice
- 구현 노트: [docs/implementation-notes.md](docs/implementation-notes.md)

실제 PG 운영망에는 연결하지 않고 `MockPaymentGateway`를 사용합니다.
데모는 무료 호스팅이라 유휴 상태에서 첫 접속이 느릴 수 있습니다.

## 기술 스택

| 구분 | 내용 |
|---|---|
| Backend | Java 17, Spring Boot 3.5, Spring MVC, Spring Data JPA |
| DB | PostgreSQL (fly/prod/local), H2 (test) · 스키마는 Flyway 마이그레이션 |
| Frontend | Thymeleaf, Vanilla JS, htmx |
| Build | Gradle 멀티모듈 (`api`, `core`) |
| Test | JUnit 5, Spring Boot Test, MockMvc, Playwright(e2e) |
| 배포 | Docker, fly.io |

## 구조

```
api  - View/REST 컨트롤러, Thymeleaf, 정적 리소스, 표준 오류 응답
core - 도메인 Entity/Repository/Service, Mock PG
```

`core`가 도메인 로직을 갖고 `api`는 그 위에 화면과 REST만 얹는다.

## 주요 흐름

### 주문 · 결제

주문 상태와 결제 상태를 따로 추적한다. 주문 처리와 PG 응답은 서로 다른 시점에 실패할 수 있어서
하나의 상태값으로 못 묶는다.

- 주문 생성 때 서버에서 판매가를 재계산하고 SKU 재고를 예약
- 멱등키 + DB unique constraint로 중복 승인/취소 방어
- PG 타임아웃·결과불명(`APPROVE_UNKNOWN`/`CANCEL_UNKNOWN`)은 실패로 단정하지 않고
  `PaymentRecoveryTask`로 분리. 확정 전까지 매출 원장을 만들지 않는다.
- Mock Gateway가 승인/실패/결과불명/망취소를 재현

### 매출 원장 · 정산 · PG 대사

- 승인(SALE)/취소(CANCEL)를 수정하지 않고 별도 불변 행으로 누적. CANCEL은 원 SALE ID를 참조한다.
- 정산은 `payment_transaction`이 아니라 SALE/CANCEL 원장을 기준으로 집계
- `DRAFT → CONFIRMED → PAID` 상태 전이. 같은 정산일·MID 중복 배치는 인메모리 락 + unique constraint로 방어
- PG 대사: 외부 정산 CSV와 내부 원장을 거래 단위로 비교해 누락·외부단독·금액차이로 분류.
  불일치가 남아 있으면 정산 확정을 막는다.
- 구매 확정(배송 완료) 후에만 정산 대상으로 전환

### 회계 (복식부기 분개장)

- 매출 원장(SALE/CANCEL)과 지급 완료 정산 명세를 소스로 복식부기 **분개**를 전기하는 프로젝션.
  결제 처리 경로는 건드리지 않고, `source_type + source_id` 유니크로 멱등. 스케줄러가 매일 자동 전기.
- SALE → (차) 미수금 / (대) 상품매출 + 부가세예수금 · CANCEL은 반대 · 정산 지급 → (차) 보통예금 + 지급수수료 + 부가세대급금 / (대) 미수금
- 각 전표·분개선에 **매장(분석 차원)** 을 상속. 시산표·손익계산서는 매장 한정 조회가 되고, "매장별 손익"은 전 매장 P&L을 한 화면에서 비교
- 시산표(총 차변 = 총 대변 검증), 총계정원장(계정별 잔액 추이), 손익계산서(수익 − 비용 = 당기순이익)

### 재고

- 모든 SKU 재고 증감을 매장 단위 원장 한 곳으로 통일. 전역 수량은 매장 재고 합계 파생값이다.
- 입고·예약·해제·출고·조정·이동을 변동 유형으로 기록하고 참조 ID를 연결
- LOT/유통기한: 입고 시 자동 발번, 출고·조정·이동은 FEFO 차감
- 이동 중 재고를 출발/도착과 분리해 이중계상 방지
- 공급처 → 발주(PO) → 입고 검수(GRN, 부분입고 허용·초과입고 차단) → LOT 발번
- 재고 실사: 시작 시 시스템 수량 스냅샷, 완료 시 차이만큼 조정 전기

### 운영 대시보드 · 분석

- 대시보드는 결과불명·복구대기·안전재고 미달·정산초안을 한 큐로 모아서 보여준다
- 분석 지표는 운영 API와 분리된 읽기 전용 API(`/api/analytics`)로 노출
- 대시보드 우하단 "AI 원인 분석" 플로팅 버튼 → 슬라이드 패널에서 실행하면 최근 24시간
  PG 실패 로그·RecoveryTask를 원인별로 그룹핑해 심각도·권장 조치를 제시(`/admin/api/insight/failure-summary`).
  원인 그룹의 출처(PG 로그/복구 작업)를 판별해 복구 작업 화면으로 딥링크한다.
  기본은 규칙 기반, `INSIGHT_PROVIDER=openai` + `OPENAI_API_KEY` 설정 시 OpenAI 구조화 출력 사용.
  전송 전 개인정보/시크릿 마스킹, 응답의 허위 참조키는 입력 표본과 대조해 제거, 호출 실패 시 규칙 기반 폴백.

## 화면

| 화면 | URL |
|---|---|
| 운영 대시보드 | `/admin/operations-dashboard` |
| 상품 / 옵션 / 카테고리 | `/admin/commerce/products` 등 |
| 주문 관리 | `/admin/commerce/orders` |
| PG 거래 | `/admin/payment-operations` |
| 복구 작업 (RecoveryTask 재시도·확인) | `/admin/payment-operations/recovery-tasks` |
| 회계 · 분개장 (전표 / 시산표 / 손익계산서) | `/admin/payment-operations/accounting` |
| 매출 원장 | `/admin/payment-operations/sales-ledger` |
| PG 대사 | `/admin/payment-operations/settlements/reconciliation` |
| 정산 관리 | `/admin/payment-operations/settlements` |
| 재고 현황 / 이동 / 입출고 내역 | `/admin/commerce/inventory` 등 |
| 공급처 / 발주 / 재고 실사 | `/admin/commerce/suppliers` 등 |
| DB 명세 | `/admin/database-spec` |
| Swagger | `/swagger-ui/index.html` |

## 테스트로 확인한 것

- 중복 승인/취소 시 기존 결과 반환, 동시 부분취소가 승인금액을 넘지 않음
- 승인 결과불명 시 SALE 미생성 + RecoveryTask 생성
- 재고 원장 정합성: 전역 수량 = 매장 재고 합계, 옵션 SKU 재고 예약
- 발주 → 입고, 재고 실사 조정이 재고·LOT·트랜잭션에 반영
- 같은 정산일·MID 중복 배치 방어, DRAFT 재실행 시 신규 매출 누적
- 표준 `ErrorResponse` / `requestId` / `fieldErrors`
- 실패 로그 AI 요약: 프로바이더 미설정 시 규칙 기반 응답, 마스킹·참조키 검증 단위 테스트
- Playwright: 상품·입고·출고·반품·운영 완결 워크플로, 운영 대시보드 AI 요약 카드 렌더

## 실행

```bash
# 빠르게: H2 인메모리 (Postgres 불필요)
./gradlew :api:bootRun            # profile = test,demo → http://localhost:8080/

# 운영과 동일하게: PostgreSQL + Flyway
docker compose up -d             # 로컬 postgres:16
./gradlew :api:bootRun --args='--spring.profiles.active=local,demo'
```

Windows는 `gradlew.bat`. 데모 시드는 `demo`/`fly` 프로파일에서만 채운다.

**스키마 관리** — `api/src/main/resources/db/migration/V*.sql` (Flyway). `V1__baseline.sql`은 엔티티에서
생성한 baseline이고, 이후 변경은 `V2`, `V3`... 로 추가한다. Postgres 프로파일은 `ddl-auto=validate`라
엔티티와 마이그레이션이 어긋나면 기동 시 실패한다.

**배포(fly.io)** — 관리형 PostgreSQL(Neon 등) JDBC URL을 secret으로 넣는다. 앱은 DB와 같은 지역에 둔다
(`fly.toml`의 `primary_region`; Neon us-east-2 ↔ fly `iad`):
```bash
fly secrets set --stage --app yeni-demo \
  DATABASE_URL='jdbc:postgresql://<host>/<db>?user=<u>&password=<pw>&sslmode=require&stringtype=unspecified&preferQueryMode=simple'
fly deploy --app yeni-demo
```
첫 기동에 Flyway가 스키마를 만들고, 데모 시더가 데이터를 채운다(이후 재기동은 count-guard로 건너뜀).
`preferQueryMode=simple` 은 `(:param IS NULL OR ...)` 식 동적 필터 쿼리에서 PG가 null 파라미터 타입을
추론하지 못하는 문제를 피하기 위한 것 — 근본적으로는 그 쿼리들을 `Specification`으로 바꾸는 게 맞다.

## 범위 밖 (운영 적용 전 보강)

- 인증/권한, CSRF/CORS, 감사 로그 정책
- 실제 PG callback signature 검증, 외부 알림 연동
- 통합 테스트 Testcontainers(실 Postgres) 전환 — 현재 H2(PostgreSQL 호환 모드)
- 동적 필터 쿼리(`ProductRepository`·`SalesTransaction*Repository`)를 `Specification`으로 전환
  (현재는 `preferQueryMode=simple`로 우회)
- RecoveryTask 자동 재처리 Worker, 정산 후 취소 자동 차감
- 영업일 기준 D+N 정산, 셀러별 정산
- 대량 데이터 성능 검증
