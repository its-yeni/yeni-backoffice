package com.yeni.backoffice.core.common.exception;

public enum ErrorCode {
    INVALID_REQUEST(400, "잘못된 요청입니다."),
    VALIDATION_ERROR(400, "요청값 검증에 실패했습니다."),
    UNAUTHORIZED(401, "인증이 필요합니다."),
    FORBIDDEN(403, "접근 권한이 없습니다."),
    NOT_FOUND(404, "요청한 대상을 찾을 수 없습니다."),
    CONFLICT(409, "요청 상태가 현재 데이터와 충돌합니다."),
    INTERNAL_SERVER_ERROR(500, "서버 처리 중 오류가 발생했습니다."),
    DATA_INTEGRITY_VIOLATION(409, "이미 처리된 요청이거나 데이터 제약 조건과 충돌합니다."),

    PRODUCT_NOT_FOUND(404, "상품을 찾을 수 없습니다."),
    PRODUCT_CODE_DUPLICATED(409, "이미 사용 중인 상품코드입니다."),
    PRODUCT_NOT_ON_SALE(409, "판매 중인 상품이 아닙니다."),
    PRODUCT_STOCK_NOT_ENOUGH(409, "상품 재고가 부족합니다."),
    PRODUCT_VARIANT_BARCODE_DUPLICATED(409, "이미 등록된 바코드입니다."),
    PRODUCT_VARIANT_NOT_FOUND(404, "SKU를 찾을 수 없습니다."),
    ORDER_NOT_FOUND(404, "주문을 찾을 수 없습니다."),
    ORDER_PAYMENT_NOT_ALLOWED(409, "현재 주문 상태에서는 결제할 수 없습니다."),
    ORDER_PAYMENT_AMOUNT_MISMATCH(409, "주문금액과 결제 요청금액이 일치하지 않습니다."),

    PAYMENT_NOT_FOUND(404, "결제 거래를 찾을 수 없습니다."),
    PAYMENT_ALREADY_APPROVED(409, "이미 승인된 주문번호입니다."),
    PAYMENT_ALREADY_CANCELED(409, "이미 전체 취소된 결제입니다."),
    PAYMENT_APPROVE_FAILED(400, "결제 승인에 실패했습니다."),
    PAYMENT_APPROVE_UNKNOWN(400, "결제 승인 결과를 확인할 수 없습니다."),
    PAYMENT_CANCEL_FAILED(400, "결제 취소에 실패했습니다."),
    PAYMENT_CANCEL_UNKNOWN(400, "결제 취소 결과를 확인할 수 없습니다."),
    PAYMENT_CANCEL_AMOUNT_EXCEEDED(400, "취소 가능 금액을 초과했습니다."),
    PAYMENT_CANCEL_KEY_REQUIRED(400, "취소 중복 방지 키가 필요합니다."),
    PAYMENT_DUPLICATED_REQUEST(409, "이미 처리된 결제 요청입니다."),
    PAYMENT_INTERNAL_SAVE_FAILED(500, "PG 승인 성공 후 내부 저장 또는 후속 처리가 실패했습니다."),
    PAYMENT_NETWORK_CANCEL_REQUIRED(409, "망취소 복구가 필요한 결제입니다."),
    PAYMENT_RECOVERY_REQUIRED(409, "복구 작업 확인이 필요한 결제입니다."),
    RECOVERY_TASK_NOT_FOUND(404, "복구 작업을 찾을 수 없습니다."),
    RECOVERY_TASK_ALREADY_COMPLETED(409, "이미 완료된 복구 작업입니다."),
    RECOVERY_TASK_NOT_CLAIMABLE(409, "이미 처리 중이거나 완료된 복구 작업입니다."),
    RECOVERY_RETRY_NOT_ALLOWED(409, "현재 복구 유형은 자동 재시도를 지원하지 않습니다."),

    SALES_TRANSACTION_NOT_FOUND(404, "매출 원장 거래를 찾을 수 없습니다."),
    SALES_DUPLICATED_LEDGER(409, "이미 생성된 매출 원장입니다."),
    SALES_LEDGER_NOT_CONFIRMED(409, "확정되지 않은 매출 원장입니다."),
    SALES_INVALID_FILTER(400, "지원하지 않는 매출 원장 필터값입니다."),
    SALES_SETTLEMENT_ALREADY_INCLUDED(409, "이미 정산에 포함된 매출 원장입니다."),

    SETTLEMENT_NOT_FOUND(404, "정산 명세를 찾을 수 없습니다."),
    SETTLEMENT_ALREADY_CONFIRMED(409, "이미 확정된 정산 명세입니다."),
    SETTLEMENT_ALREADY_PAID(409, "이미 지급 완료된 정산 명세입니다."),
    SETTLEMENT_RECALCULATE_NOT_ALLOWED(409, "정산 확정 이후에는 재계산할 수 없습니다."),
    SETTLEMENT_POLICY_NOT_FOUND(404, "적용 가능한 정산 수수료 정책을 찾을 수 없습니다."),
    SETTLEMENT_EMPTY_TARGET(400, "정산 대상 매출 원장이 없습니다."),
    SETTLEMENT_INVALID_STATUS(409, "정산 상태가 현재 작업에 적합하지 않습니다."),
    SETTLEMENT_DUPLICATE_EXECUTION(409, "동일 정산일과 MID 기준의 정산 배치가 이미 실행 중이거나 완료되었습니다."),
    SETTLEMENT_FEE_POLICY_INVALID(400, "정산 수수료 정책 요청값이 올바르지 않습니다.");

    private final int status;
    private final String defaultMessage;

    ErrorCode(int status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public int getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
