package com.yeni.backoffice.core.commerce.enums;

/**
 * "Mock 주문 생성" 화면에서 관리자가 고를 수 있는 결제 시나리오.
 * 각 값은 Mock PG(MockPaymentGateway)가 orderNo에 심어둔 마커로 분기하는 방식과 짝을 이룬다.
 */
public enum MockOrderScenario {
    NORMAL,               // 정상 승인
    RESULT_UNKNOWN,        // 결과 불명 (응답 딜레이)
    INTERNAL_FAIL,          // 승인 후 내부 처리 실패 (망취소)
    PAYMENT_METHOD_ERROR,   // 결제 수단 오류
    CARD_LIMIT_EXCEEDED,    // 카드 한도 부족
    DUPLICATE_REQUEST       // 듀플리케이션 테스트 (중복 요청)
}
