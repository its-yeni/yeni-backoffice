package com.yeni.backoffice.core.commerce.event;

import java.math.BigDecimal;

/**
 * 반품 검수가 통과해 환불이 확정됐을 때 발행되는 이벤트.
 * Return 도메인은 이 이벤트를 발행하는 것으로 역할이 끝나고, "환불을 실제로 어떻게 처리할지"(PG 취소 호출,
 * 그에 따른 취소 매출 원장 생성, 정산 반영)는 결제/정산 쪽 리스너가 구독해서 처리한다 — 두 도메인을 직접 호출로
 * 엮지 않고 이벤트로만 연결해서, Return 도메인이 결제 내부 구현을 몰라도 되게 하기 위해서다.
 */
public record ReturnCompletedEvent(
        Long returnId,
        Long orderId,
        String orderNo,
        Long paymentId,
        BigDecimal refundAmount,
        String reason
) {}
