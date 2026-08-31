package com.yeni.backoffice.core.commerce.enums;

/**
 * 재고 실사(Stock Count) 상태.
 * IN_PROGRESS(실사 중, 라인별 카운트 입력) → COMPLETED(반영 완료, 차이만큼 재고 조정 전기).
 * CANCELED: 실사 중 취소(재고 미반영).
 */
public enum StockCountStatus {
    IN_PROGRESS,
    COMPLETED,
    CANCELED
}
