package com.yeni.backoffice.core.commerce.enums;

/**
 * SKU 재고 변동 이력의 유형.
 * RECEIPT: 입고(현재재고 증가). RESERVE: 주문 생성으로 예약(예약재고만 증가, 현재재고는 그대로).
 * RELEASE: 결제 실패/취소로 예약 해제(예약재고만 감소). SHIPMENT: 출고 완료(현재재고·예약재고 둘 다 감소).
 * ADJUST_IN/ADJUST_OUT: 실사 등에 의한 수동 재고 조정.
 * TRANSFER_OUT/TRANSFER_IN: 매장(창고) 간 재고 이동 출고/입고.
 */
public enum InventoryTransactionType {
    RECEIPT,
    RESERVE,
    RELEASE,
    SHIPMENT,
    ADJUST_IN,
    ADJUST_OUT,
    TRANSFER_OUT,
    TRANSFER_IN
}
