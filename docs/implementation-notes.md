# Implementation Notes

결제·취소, 매출 원장, 복구 작업, 후속 처리 Queue, 정산을 구현하면서 흐름을 어떤 기준으로 나눴고
중복 요청·동시 처리·결과불명을 코드에서 어떻게 다뤘는지 정리한 메모.

실제 PG/외부망에는 붙지 않고 `MockPaymentGateway` 등으로 성공·실패·결과불명·재처리를 재현한다.
재고·발주·실사 쪽은 [README](../README.md)의 "주요 흐름" 참고.

기본 원칙:

- 확정되지 않은 결제 결과는 매출 원장·정산에 반영하지 않는다
- 중복 생성 방어는 서비스 사전 조회 + DB unique constraint 병행
- 같은 자원을 동시에 바꾸는 흐름은 row lock 또는 상태 조건부 claim
- 외부 후속 처리는 결제 저장 흐름과 분리해 Queue 상태로 추적

## 2. 전체 처리 흐름

```text
관리자 요청
  -> 결제 승인 또는 취소 요청
  -> MockPaymentGateway 호출
  -> PaymentTransaction / PaymentCancel 저장
  -> 확정 거래만 SALE / CANCEL 매출 원장 생성
  -> ExternalSendRequest / AlimtalkQueue 생성
  -> Mock Worker가 Queue 후속 처리
  -> 결과불명 또는 내부 처리 실패는 PaymentRecoveryTask로 분리
  -> SALE / CANCEL 매출 원장을 기준으로 정산 생성
  -> DRAFT -> CONFIRMED -> PAID 상태 전이
```

| 도메인 | 역할 |
|---|---|
| `PaymentTransaction` | 승인 결과, 승인 금액, 누적 취소 금액과 결제 상태 관리 |
| `PaymentCancel` | 취소 요청 키와 취소 결과 관리 |
| `SalesTransaction` | 정산 기준이 되는 SALE/CANCEL 매출 원장 |
| `PaymentRecoveryTask` | 결과불명과 복구 필요 작업의 상태·재처리 추적 |
| `ExternalSendRequest` | 외부전송 후속 처리 Queue |
| `AlimtalkQueue` | 알림톡 후속 처리 Queue |
| `SettlementStatement` | 정산일·MID 기준 정산 명세와 상태 관리 |
| `SettlementDetail` | 정산 명세에 포함된 매출 원장 snapshot |

관련 코드는 `core/src/main/java/com/yeni/backoffice/core/payment` 아래에 도메인별 Entity, Repository, Service로 구분되어 있습니다.

## 3. 결제 승인/취소 흐름

결제 흐름은 실제 PG 운영망 대신 `PaymentGateway` 인터페이스와 `MockPaymentGateway` 구현체를 사용합니다. Mock 구현은 주문번호와 요청 키를 기준으로 승인 성공, 실패, 결과불명과 취소 결과를 재현합니다.

승인 성공 시 `PaymentApproveService`가 `PaymentTransaction`을 저장하고 `SalesLedgerService`를 통해 SALE 원장을 생성합니다. 이후 외부전송 요청과 알림톡 Queue를 생성합니다.

취소 성공 시 `PaymentCancelService`가 `PaymentCancel`을 저장하고 결제의 누적 취소 금액과 상태를 갱신합니다. 취소 확정 거래는 원 SALE을 수정하지 않고 별도의 음수 CANCEL 원장으로 기록합니다.

결과가 확정되지 않은 `APPROVE_UNKNOWN`, `CANCEL_UNKNOWN` 상태에서는 SALE/CANCEL 원장을 바로 생성하지 않습니다. 확정되지 않은 거래가 정산 대상에 포함되는 것을 막기 위한 기준입니다.

관련 코드:

- `PaymentGateway`, `PaymentGatewayRegistry`, `MockPaymentGateway`
- `PaymentApproveService`, `PaymentCancelService`, `PaymentQueryService`
- `PaymentTransaction`, `PaymentCancel`
- `SalesLedgerService`, `PaymentNotificationService`

현재는 Mock 호출이 서비스 흐름 안에서 빠르게 종료됩니다. 실제 외부 PG 호출은 응답 지연과 통신 장애를 고려해 외부 호출 구간과 DB 트랜잭션 경계를 더 세분화할 필요가 있습니다.

## 4. 중복 요청 방어

결제와 취소 요청은 사용자 중복 클릭, 네트워크 재시도, 서버 재호출로 반복될 수 있습니다. 서비스 계층에서 기존 데이터를 먼저 조회해 동일 요청의 기존 결과를 반환하고, 동시에 요청이 들어오는 상황의 최종 방어는 DB unique constraint가 담당합니다.

| 대상 | 중복 기준 | 방어 목적 | 구현 상태 |
|---|---|---|---|
| 결제 승인 | `orderNo`, `tid`, `approvalRequestKey` | 중복 승인 데이터 생성 방어 | 구현 |
| 결제 취소 | `cancelRequestKey` | 중복 취소 데이터 생성 방어 | 구현 |
| 매출 원장 | `sourceType + sourceId` | SALE/CANCEL 중복 생성 방어 | 구현 |
| RecoveryTask | `taskKey` | 복구 작업 중복 생성 방어 | 구현 |
| 외부전송 Queue | `requestKey` | 외부전송 요청 중복 생성 방어 | 구현 |
| 알림톡 Queue | `messageKey` | 알림톡 Queue 중복 생성 방어 | 구현 |
| 정산 명세 | `settlementDate + mid` | 동일 기준 정산 명세 중복 생성 방어 | 구현 |

Entity의 `@UniqueConstraint`와 DB migration의 unique index가 최종 데이터 제약을 구성합니다. 서비스 사전 조회만으로는 동시 요청을 완전히 막기 어렵기 때문에 DB 제약을 함께 사용합니다.

## 5. 부분취소 동시 처리

부분취소는 같은 결제 건의 남은 취소 가능 금액을 여러 요청이 동시에 변경할 수 있는 흐름입니다. 예를 들어 승인금액 10,000원에 8,000원 취소 요청 두 건이 동시에 처리되면, 잠금 없이 각각 이전 취소 금액을 읽어 승인금액을 초과할 수 있습니다.

`PaymentCancelService`는 `PaymentTransactionRepository.findByIdForUpdate()`로 대상 결제 PK row에 `PESSIMISTIC_WRITE` lock을 적용합니다. 잠금을 획득한 뒤 최신 누적 취소 금액을 기준으로 남은 취소 가능 금액을 다시 계산하고 요청 금액을 검증합니다.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select p from PaymentTransaction p where p.id = :id")
Optional<PaymentTransaction> findByIdForUpdate(@Param("id") Long id);
```

이 잠금은 `payment_transaction` 전체가 아니라 조회한 결제 row를 대상으로 합니다. 서로 다른 `paymentId`의 취소 요청은 서로 다른 row를 대상으로 처리됩니다.

관련 코드:

- `PaymentTransactionRepository.findByIdForUpdate`
- `PaymentCancelService.cancelPaymentBridge`
- `PaymentTransaction.applyCancel`

현재 흐름은 Mock PG 호출을 사용하므로 서비스 트랜잭션 안에서 취소 처리가 구성되어 있습니다. 실제 PG 호출 지연이 있는 환경에서는 row lock 유지 시간이 길어질 수 있으므로 취소 요청 선점, 외부 호출, 결과 확정의 트랜잭션 분리를 검토해야 합니다.

## 6. 결과불명과 RecoveryTask

PG 통신에서는 응답 유실이나 결과불명 상황이 발생할 수 있습니다. 이 프로젝트는 결과가 확인되지 않은 요청을 임의로 성공 또는 실패로 확정하지 않고 `APPROVE_UNKNOWN`, `CANCEL_UNKNOWN` 상태와 `PaymentRecoveryTask`로 분리합니다.

승인 결과불명은 `PaymentQueryService.retryQuery()`를 통해 재조회할 수 있습니다. 재조회 결과 승인 성공이 확인되면 누락된 SALE 원장, 외부전송 요청, 알림톡 Queue를 생성합니다. 생성 과정의 unique constraint와 서비스 중복 확인을 통해 동일 후속 처리가 반복 생성되지 않도록 구성했습니다.

RecoveryTask 재처리는 `READY` 또는 `FAILED` 상태만 조건부 update로 `PROCESSING` claim합니다. 동일 task에 재처리 요청이 동시에 들어오면 update count가 1인 요청만 실제 재처리를 수행합니다. `PROCESSING` 또는 `SUCCESS` 상태는 claim할 수 없습니다.

claim과 최종 상태 저장은 `REQUIRES_NEW` 트랜잭션으로 분리되어 있습니다. 재처리 도중 오류가 발생해도 실패 사유와 `FAILED` 상태를 별도 트랜잭션으로 기록해 `PROCESSING` 상태 고착 가능성을 줄입니다.

관련 코드:

- `PaymentRecoveryTask`, `PaymentRecoveryTaskRepository.claimForRetry`
- `PaymentRecoveryOperationService`, `RecoveryTaskRecorder`
- `PaymentRecoveryOperationService`
- `RecoveryTaskClaimService`, `RecoveryTaskStateService`
- `PaymentQueryService.retryQuery`

현재 자동 재처리는 `APPROVE_UNKNOWN_CHECK` 유형을 지원합니다. 취소 결과 재조회, 망취소, 외부전송·알림톡 RecoveryTask 자동 재처리는 운영 확장 범위입니다.

## 7. 후속 처리 Queue와 Mock Worker

외부전송과 알림톡은 결제 트랜잭션에서 직접 외부 API를 호출하지 않고 `ExternalSendRequest`, `AlimtalkQueue`로 분리합니다. 결제와 매출 원장이 저장된 뒤 후속 처리 상태를 독립적으로 확인하고 재시도할 수 있도록 구성한 것입니다.

수동 실행 Mock Worker는 처리 가능한 Queue ID를 조회한 뒤 각 건을 조건부 update로 `SENDING` claim합니다. 외부전송은 `READY/FAILED`, 알림톡은 기존 호환 상태를 포함해 `READY/FAILED/RETRY_READY`를 처리 대상으로 사용합니다.

claim 성공 건만 `MockExternalSendClient` 또는 `MockAlimtalkClient`로 처리합니다. 성공 시 `SUCCESS`, 실패 시 `FAILED`로 전이하며 `retryCount`, `lastErrorMessage`, 처리 시각을 기록합니다. retry limit에 도달한 Queue는 처리 대상 조회와 claim에서 제외합니다.

Worker 전체를 하나의 긴 트랜잭션으로 묶지 않습니다. claim과 개별 Queue 처리를 `REQUIRES_NEW` 단위로 분리해 한 건의 실패가 나머지 Queue 처리를 중단하지 않도록 구성했습니다. 동일 Queue를 여러 Worker가 동시에 조회하더라도 상태 조건부 update에 성공한 Worker 하나만 처리합니다.

관련 코드:

- `ExternalSendRequest`, `ExternalSendRequestRepository.claimForSend`
- `ExternalSendWorkerService`, `ExternalSendWorkerClaimService`, `ExternalSendWorkerProcessor`
- `MockExternalSendClient`
- `AlimtalkQueue`, `AlimtalkQueueRepository.claimForSend`
- `AlimtalkWorkerService`, `AlimtalkWorkerClaimService`, `AlimtalkWorkerProcessor`
- `MockAlimtalkClient`, `FollowUpWorkerFailureService`

현재 구현은 관리자 화면과 REST API에서 수동으로 Worker를 실행하는 Mock 기반 구조입니다. 실제 외부 시스템 연동, 자동 스케줄링, 장시간 `SENDING` 복구, dead-letter 처리, 분산 Worker와 `SKIP LOCKED` 적용은 운영 확장 범위입니다.

## 8. 매출 원장 설계

결제 상태와 정산 기준 데이터를 분리하기 위해 `SalesTransaction` 매출 원장을 사용합니다. 승인 성공은 SALE 원장, 취소 성공은 음수 CANCEL 원장으로 저장합니다. 취소 시 원 SALE을 수정하지 않고 원 SALE ID를 참조하는 별도 거래를 추가합니다.

이 방식은 승인과 취소 이력을 모두 보존하고, 특정 시점의 확정 거래를 기준으로 정산 대상을 구성하기 위한 설계입니다. 결과불명 상태에서는 원장을 생성하지 않으며, 재조회로 결과가 확정된 뒤 원장을 생성합니다.

매출 원장 조회는 DB-side paging을 사용하고, 화면 요약은 Repository 집계 쿼리로 계산합니다.

관련 코드:

- `SalesTransaction`
- `SalesLedgerService`
- `SalesTransactionRepository.searchLedger`
- `SalesTransactionRepository.summarizeLedger`
- `SettlementBatchProcessor`

대량 데이터 운영 환경에서는 실제 DB 기준 실행 계획, 복합 인덱스, paging 방식, 집계 성능을 추가로 검증해야 합니다.

## 9. 정산 생성과 중복 방어

정산은 `PaymentTransaction`을 직접 집계하지 않고, 정산일의 미정산 SALE/CANCEL 매출 원장을 기준으로 생성합니다. `SettlementBatchProcessor`가 정산 대상 원장과 수수료 정책을 읽어 `SettlementStatement`, `SettlementDetail`, `SettlementFeeDetail`을 구성합니다.

동일 `settlementDate + mid` 기준의 동시 실행은 `SettlementOperationService`의 keyed `ReentrantLock`으로 같은 JVM 안에서 직렬화합니다. 최종 데이터 방어는 `SettlementStatement`의 DB unique constraint가 담당하며, 충돌은 `SETTLEMENT_DUPLICATE_EXECUTION`으로 변환합니다.

기존 DRAFT 명세가 있고 신규 미정산 원장이 존재하면 같은 정산 명세에 누적해 재계산합니다. CONFIRMED 또는 PAID 명세는 같은 기준으로 다시 계산하지 않습니다.

관련 코드:

- `SettlementOperationService.runDailySettlement`
- `SettlementBatchProcessor.process`
- `SettlementStatement`, `SettlementDetail`, `SettlementFeeDetail`
- `SettlementStatementRepository`

현재 keyed lock은 단일 JVM 범위입니다. 여러 애플리케이션 인스턴스가 동시에 실행되는 환경에서는 DB lock, Redis 기반 distributed lock, 배치 실행 테이블 선점 같은 방식을 추가로 검토해야 합니다.

## 10. 정산 상태 전이

정산 명세는 다음 상태로 관리합니다.

```text
DRAFT -> CONFIRMED -> PAID
```

- `DRAFT`: 정산 초안이며 신규 미정산 매출을 누적해 재계산할 수 있습니다.
- `CONFIRMED`: 정산 기준이 확정된 상태로 재계산을 차단합니다.
- `PAID`: 지급 처리가 완료된 상태로 추가 상태 변경을 제한합니다.

`SettlementOperationService.confirmStatement()`는 DRAFT 상태만 확정할 수 있으며, `markPaid()`는 CONFIRMED 상태만 지급 완료로 전이할 수 있습니다. 상태 전이 시 연결된 매출 원장 상태와 정산 로그도 함께 갱신합니다.

확정 또는 지급 이후 취소를 다음 정산에서 자동 차감하는 흐름은 아직 구현 범위에 포함되지 않습니다.

## 11. 오류 응답 표준화

API 오류는 `BusinessException`과 `ErrorCode`를 기준으로 처리하고 `ApiExceptionAdvice`에서 공통 응답으로 변환합니다.

`ErrorResponse`는 다음 정보를 포함합니다.

- `code`: 도메인 오류 코드
- `message`: 사용자 또는 운영자가 확인할 오류 메시지
- `path`: 요청 경로
- `requestId`: 요청 단위 추적 값
- `fieldErrors`: Bean Validation 실패 필드 목록

`RequestIdFilter`가 requestId를 생성하거나 요청 헤더 값을 사용하고 MDC에 저장합니다. `ApiExceptionAdvice`는 비즈니스 충돌, 검증 실패, 예상하지 못한 시스템 오류를 구분해 응답과 로그를 남깁니다.

관련 코드:

- `BusinessException`, `ErrorCode`
- `ApiExceptionAdvice`, `ErrorResponse`, `FieldErrorResponse`
- `RequestIdFilter`

실제 운영 환경에서는 민감정보 마스킹, 로그 보관 정책, 알림 기준, 외부 관측 도구 연동을 추가로 구성해야 합니다.

## 12. 테스트로 검증한 내용

통합 테스트는 `api/src/test/java/com/yeni/backoffice/api/YeniBackofficeApplicationTests.java`에 구성되어 있으며, 일부 서비스 단위 테스트는 `core/src/test`에 있습니다.

| 구분 | 검증 내용 | 대표 테스트 |
|---|---|---|
| 결제 | 동일 승인 요청이 기존 결제를 반환하고 SALE 원장이 중복 생성되지 않는지 검증 | `duplicateApproveRequestReturnsExistingPaymentAndCreatesOneSale` |
| 취소 | 같은 결제 row에 대한 동시 부분취소가 승인금액을 초과하지 않는지 검증 | `concurrentPartialCancelCreatesOnlyAllowedAmount` |
| 취소 | 서로 다른 결제 row의 부분취소가 독립적으로 처리되는지 검증 | `partialCancelLockDoesNotBlockDifferentPaymentRows` |
| 결과불명 | 승인 결과불명 시 SALE 원장이 생성되지 않고 RecoveryTask가 생성되는지 검증 | `approveUnknownCreatesRecoveryTask` |
| 복구 | 재조회 성공 후 SALE 원장과 후속 Queue가 한 번만 생성되는지 검증 | `retryQueryApproveUnknownSuccessCreatesSaleAndFollowUps` |
| 복구 | 동일 RecoveryTask 동시 재처리 중 하나만 claim하는지 검증 | `concurrentRecoveryRetryIsClaimedOnce` |
| 외부전송 | Mock Worker 성공·실패와 retry limit을 검증 | `processReadyExternalSendRequestSuccess`, `processReadyExternalSendRequestFailure`, `externalSendRetryLimitExceededIsSkipped` |
| 알림톡 | Mock Worker 성공·실패와 retry limit을 검증 | `processReadyAlimtalkMessageSuccess`, `processReadyAlimtalkMessageFailure`, `alimtalkRetryLimitExceededIsSkipped` |
| 후속 처리 | 동일 Queue를 두 Worker가 조회해도 하나만 claim하는지 검증 | `concurrentExternalSendWorkerClaimsOnce`, `concurrentAlimtalkWorkerClaimsOnce` |
| 정산 | 같은 날짜 정산 재실행 시 동일 DRAFT 명세를 유지하는지 검증 | `runningSameDraftSettlementTwiceReturnsExistingSingleStatement` |
| 정산 | DRAFT 누적 재계산과 CONFIRMED/PAID 상태 제한을 검증 | `rerunningDraftSettlementIncludesNewSalesWithoutCreatingAnotherStatement`, `confirmedAndPaidSettlementStatusTransitionsAreRestricted` |
| 오류 응답 | requestId와 fieldErrors를 포함한 표준 오류 응답을 검증 | `validationErrorResponseContainsRequestIdAndFieldErrors` |

동시성 테스트는 H2 테스트 환경에서 실행됩니다. 실제 운영 DB의 lock wait, isolation level, deadlock 특성은 다를 수 있으므로 운영 DB 기반 통합 테스트가 추가로 필요합니다.

## 13. 현재 구현 범위와 운영 확장 방향

### 현재 구현 범위

- Mock PG Adapter 기반 승인·취소·결과불명 재현
- Idempotency Key와 DB unique constraint 기반 중복 생성 방어
- 부분취소 `PESSIMISTIC_WRITE` 단일 row lock
- SALE/CANCEL 매출 원장과 DB-side 조회·집계
- 결과불명 RecoveryTask와 조건부 claim 재처리
- 외부전송·알림톡 Queue와 수동 실행 Mock Worker
- Queue 상태 조건부 claim과 retry limit
- 정산 DRAFT/CONFIRMED/PAID 상태 전이
- 정산 keyed lock과 DB unique constraint 중복 방어
- ErrorCode, requestId, fieldErrors 기반 오류 응답

### 운영 확장 시 고려할 항목

- 실제 PG callback/webhook signature 검증과 외부망 연동
- 외부 PG 호출과 DB 트랜잭션 경계 분리
- 취소 결과 조회와 망취소 자동 처리
- Redis 또는 DB 기반 distributed lock
- `SKIP LOCKED` 기반 다중 Worker claim
- Outbox Pattern과 메시지 브로커 연동
- Worker 자동 스케줄링, 장기 `SENDING` 복구, dead-letter 처리
- 운영 DB 기준 대량 데이터 성능과 동시성 검증
- 정산 후 취소의 다음정산차감 자동 반영
- 영업일·공휴일 기준 D+N 정산과 셀러별 정산
- 관리자 권한, 감사 로그 정책, 민감정보 마스킹

## 14. 재고·물류 흐름

재고 도메인은 일반 커머스/유통 백오피스의 입고 흐름을 따른다.

```text
발주서(PurchaseOrder) 작성(DRAFT)
  -> 발주 확정(ORDERED)        : 미입고 수량이 "입고 예정"으로 잡힘
  -> 입고 검수(GRN, 부분입고 가능): PurchaseOrderService.receive
  -> 재고 원장(InventoryLedgerService)이 매장 재고·LOT·감사 로그를 한 번에 반영
```

주기적으로는 재고 실사(StockCount)로 시스템 수량과 실물을 대조하고, 차이만큼 조정을 전기한다.

### 단일 재고 원장 (`InventoryLedgerService`)

SKU 재고 증감(입고/예약/예약해제/출고/조정/이동)의 유일한 쓰기 경로다. 모든 변동은
매장별 재고(`StoreVariantInventory`)와 LOT(`InventoryLot`)에 먼저 반영하고, 그 직후 해당 SKU의
전역 프로젝션(`ProductVariant.stockQuantity`/`reservedQuantity`)을 `Σ 매장 재고`로 다시 맞춘다.
전역 수량은 이제 독립 장부가 아니라 매장 합계의 파생값이므로, 예전에 있던 "전역 vs 매장" 드리프트
보정 코드는 제거했다.

- 첫 매장 재고 행이 생길 때는 그동안 전역 수량으로만 관리되던 기존 재고를 그 매장으로 귀속시킨다(단일 매장 가정).
- 매장 재고 행이 하나도 없는 경로(옵션 없는 상품, 순수 단위 테스트)는 기존 `ProductVariant` 직접 증감 메서드를 폴백으로 유지한다.

### LOT / 유통기한 추적

- 입고 시 LOT를 항상 자동 발번한다(`LOT-yyMMdd-###`). 반품 재입고는 `RTN-`, 실사·수기 증가 조정은 `ADJ-`, 매장 이동 입고는 출발 LOT + `-T`.
- 유통기한이 없는 품목(의류 등)도 LOT 추적 대상이므로 `inventory_lot.expiration_date` 는 nullable, FEFO 정렬은 `NULLS LAST`.
- 출고·이동·감소 조정은 FEFO(유통기한 임박 순)로 LOT 잔량을 소진한다.
- 재고 현황 화면은 `Σ LOT 잔량 ≠ 매장 현재재고` 일 때 "LOT 불일치" 배지를 노출한다(원장 통일 이후에는 과거·직접 편집 데이터에서만 나타난다).

### 발주 제안

`InventoryPlanningService.replenishments` 는
`일평균 = max(0, 30일 출고 − 30일 반품 재입고) / 30`,
`리드타임 = 그 SKU를 가장 최근 발주한 공급처의 lead_time_days`,
`공급 = 가용 + 이동 중 + 발주 미입고` 로 발주점과 제안 수량을 계산한다.

### 관련 코드

- `InventoryLedgerService`, `PurchaseOrderService`, `StockCountService`, `SupplierService`
- `LocationInventoryService`(입고 예정·LOT 잔량·매장 안전재고), `InventoryPlanningService`(발주 제안·LOT)
- Entity: `Supplier`, `PurchaseOrder`(+Item), `StockCount`(+Line), `InventoryLot`, `StoreVariantInventory.safetyStock`
- 화면: 공급처 관리 / 발주 관리 / 입고 처리 / 재고 현황 / 재고 실사 / LOT·유통기한 / 발주 제안 / 재고 이동
- 테스트: `InventoryDomainTest`(원장 프로젝션 정합, 발주 상태 전이·부분입고, 실사 조정 전기)

### 운영 확장 시 고려할 항목

- 공급처 EDI/발주 승인 워크플로, 입고 검수 반려·부분 불량 처리
- 순환 실사(cycle count) 스케줄러, 실사 중 재고 동결
- 멀티 매장에서 전역 프로젝션의 의미 재정의(대표 매장 vs 합계), 매장별 원가 분리
- 발주-입고 리드타임 실측 피드백, 공급처별 정산

---

이 문서는 현재 저장소에 구현된 구조를 기준으로 작성했습니다. 실제 외부 시스템과 운영 인프라가 필요한 항목은 확장 방향으로 분리했습니다.
