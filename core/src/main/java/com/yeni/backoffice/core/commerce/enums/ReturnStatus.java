package com.yeni.backoffice.core.commerce.enums;

/**
 * 반품 접수부터 처리 완료까지의 상태.
 * REQUESTED(접수) → INSPECTING(회수/검수 중) → COMPLETED(검수 완료: 재고 복원 + 환불 확정) 또는 REJECTED(검수 반려: 환불 없음).
 * 재고는 REQUESTED 시점에 바로 복원하지 않는다 — 손상품이 검수 전에 판매 가능 재고로 잡히는 사고를 막기 위해
 * COMPLETED(검수 통과) 시점에만 복원한다.
 */
public enum ReturnStatus {
    REQUESTED,
    INSPECTING,
    COMPLETED,
    REJECTED
}
