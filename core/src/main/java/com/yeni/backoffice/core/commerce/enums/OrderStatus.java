package com.yeni.backoffice.core.commerce.enums;

/**
 * 주문 생애주기 상태.
 * PENDING_PAYMENT(결제 대기) → PAID(결제 완료) → PURCHASE_CONFIRMED(배송 완료 후 구매 확정, 매출 확정 시점)
 * 이 정상 흐름이고, PAYMENT_FAILED / PARTIALLY_CANCELLED / CANCELLED 는 분기 상태다.
 * CREATED 는 결제 이전 초안 주문(관리자 수기 생성 등)을 위한 상태다.
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    CREATED,
    PAID,
    PURCHASE_CONFIRMED,
    PAYMENT_FAILED,
    PARTIALLY_CANCELLED,
    CANCELLED
}
