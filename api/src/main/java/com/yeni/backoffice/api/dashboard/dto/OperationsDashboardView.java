package com.yeni.backoffice.api.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record OperationsDashboardView(
        LocalDateTime computedAt,
        int salesTrendDays,

        long actionRequiredCount,
        long unknownPaymentCount,
        long failedPaymentCount,
        long recoveryTaskCount,
        long draftSettlementCount,
        long lowStockSkuCount,
        long soldOutSkuCount,
        long pendingShipmentCount,
        long refundFailedCount,
        long returnRequestedCount,
        long deliveryDelayedCount,

        // "오늘 현황" 카드 4개: 실제 일별 흐름 지표라 전일 대비 증감을 함께 낼 수 있다.
        long todayOrderCount,
        Double todayOrderChangePercent,
        BigDecimal todayRevenue,
        Double todayRevenueChangePercent,
        long todayReturnRequestedCount,

        // "즉시 처리 필요" 4개는 개별 예외 큐 항목 중 가장 시급한 것만 요약해서 보여준다(전체 목록은 큐 테이블에).
        long paymentIssueCount,
        long logisticsIssueCount,
        long inventoryIssueCount,
        long returnSettlementIssueCount,

        // "오늘 처리 현황" 진행률 4개: (오늘 처리 완료 / (오늘 처리 완료 + 현재 잔여)) — 오늘 새로 발생한 일감을
        // 오늘 중 얼마나 소화했는지 보여준다. 잔여가 0이고 처리도 0이면 진행률 0%(할 일이 없었다는 뜻)로 둔다.
        ProcessingRatio orderProcessing,
        ProcessingRatio shipmentProcessing,
        ProcessingRatio returnProcessing,
        ProcessingRatio settlementProcessing,

        List<DailySalesPoint> salesTrend,
        List<StorePerformance> storePerformance,
        List<OperationsQueueItem> queueItems) {

    public record ProcessingRatio(long done, long total) {
        public int percent() { return total == 0 ? 0 : (int) Math.round(done * 100.0 / total); }
    }

    public record DailySalesPoint(LocalDate date, BigDecimal revenue, long orderCount) {}

    public record StorePerformance(Long storeId, String storeName, long orderCount, BigDecimal confirmedRevenue) {}

    public record OperationsQueueItem(
            String priority,
            String domain,
            String title,
            String description,
            long count,
            String actionLabel,
            String actionUrl) {}
}
