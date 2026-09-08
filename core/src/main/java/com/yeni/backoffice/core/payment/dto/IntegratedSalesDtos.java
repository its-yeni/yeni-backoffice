package com.yeni.backoffice.core.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class IntegratedSalesDtos {

    /** 주문 1건의 전체 파이프라인 상태 — 주문 → 결제 → 배송 → 매출원장 → 구매확정 → 대사 → 정산 → 지급. */
    public record IntegratedSaleRow(
            Long orderId,
            String orderNo,
            LocalDateTime orderedAt,
            String storeName,
            String channelType,
            String productSummary,
            int itemCount,
            BigDecimal amount,
            BigDecimal cancelledAmount,
            String paymentStatus,
            Long paymentId,
            String cardBrief,
            String acquiringStatus,
            String deliveryStatus,
            String ledgerState,          // NONE / SALE / PARTIAL_CANCEL / CANCEL
            boolean purchaseConfirmed,
            String reconState,           // NONE / MATCH / MISMATCH
            String settlementState,      // NONE / DRAFT / CONFIRM_REQUESTED / CONFIRMED / PAYOUT_REQUESTED / PAID
            Long settlementStatementId,
            String stage,                // 파이프라인 단계 키 (필터/정렬용)
            String nextLabel,
            String nextHref
    ) {
    }

    public record IntegratedSalesResponse(
            int count,
            BigDecimal totalAmount,
            BigDecimal netAmount,
            List<StageCount> stageCounts,
            List<IntegratedSaleRow> rows
    ) {
    }

    public record StageCount(String stage, String label, long count) {
    }
}
