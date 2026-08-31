package com.yeni.backoffice.core.commerce.enums;

/**
 * 반품 검수 완료 이후 "실제 환불(PG 취소)"이 어떻게 됐는지를 별도로 추적한다.
 * 환불은 검수 완료 트랜잭션이 커밋된 뒤 비동기 이벤트 리스너가 처리하므로, 반품 상태(ReturnStatus)가
 * COMPLETED라고 해서 돈이 실제로 나갔다는 보장이 없다 — 화면에서 이 둘을 분리해서 보여줘야
 * "완료라고 떴는데 환불은 안 됐다"는 걸 운영자가 놓치지 않는다.
 */
public enum RefundStatus {
    /** 환불할 금액이 0원이라 PG 취소 자체가 필요 없는 경우. */
    NOT_REQUIRED,
    /** 검수는 완료됐고 환불 이벤트를 발행했지만, 아직 PG 취소 처리 결과를 받지 못한 상태. */
    PENDING,
    /** PG 취소(환불)까지 실제로 성공. */
    SUCCESS,
    /** PG 취소가 실패했고, 반품/재고 처리는 이미 커밋됐으므로 수동 확인이 필요한 상태. */
    FAILED
}
