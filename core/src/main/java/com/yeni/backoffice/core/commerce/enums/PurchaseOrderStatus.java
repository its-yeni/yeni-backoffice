package com.yeni.backoffice.core.commerce.enums;

/**
 * 발주(Purchase Order) 상태.
 * DRAFT(작성 중) → ORDERED(발주 확정, 입고 예정 수량 확보) → PARTIALLY_RECEIVED(부분 입고) → RECEIVED(입고 완료).
 * CANCELED: 미입고 상태에서 취소.
 */
public enum PurchaseOrderStatus {
    DRAFT,
    ORDERED,
    PARTIALLY_RECEIVED,
    RECEIVED,
    CANCELED
}
