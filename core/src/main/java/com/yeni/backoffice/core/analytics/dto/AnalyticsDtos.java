package com.yeni.backoffice.core.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Power BI(Web Connector → Power Query) 소비를 고려한 <b>flat</b> 분석 응답 DTO 모음.
 * 금액은 순수 숫자(문자열/단위 표기 금지), 날짜는 ISO(yyyy-MM-dd) 로 반환한다.
 * 집계 로직은 {@code AnalyticsService} 에만 두고, 업무 서비스는 재사용만 한다.
 */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    /** 공통 조회 필터. 값이 비어 있으면 최근 30일(오늘 포함) 로 채운다. */
    public record AnalyticsFilter(
            LocalDate startDate,
            LocalDate endDate,
            String channel,
            Long storeId,
            String paymentMethod
    ) {
        public static AnalyticsFilter of(LocalDate startDate, LocalDate endDate,
                                         String channel, Long storeId, String paymentMethod) {
            LocalDate end = endDate != null ? endDate : LocalDate.now();
            LocalDate start = startDate != null ? startDate : end.minusDays(29);
            if (start.isAfter(end)) {
                LocalDate tmp = start;
                start = end;
                end = tmp;
            }
            return new AnalyticsFilter(start, end, blankToNull(channel), storeId, blankToNull(paymentMethod));
        }

        public long dayCount() {
            return ChronoUnit.DAYS.between(startDate, endDate) + 1;
        }

        /** 직전 동일 기간(추이 비교용). */
        public AnalyticsFilter previousPeriod() {
            long days = dayCount();
            return new AnalyticsFilter(startDate.minusDays(days), startDate.minusDays(1), channel, storeId, paymentMethod);
        }

        public boolean matchesDate(LocalDate date) {
            return date != null && !date.isBefore(startDate) && !date.isAfter(endDate);
        }

        private static String blankToNull(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }
    }

    /** 운영 개요 KPI. 변화율은 직전 동일 기간 대비 %(소수 1자리). */
    public record OverviewResponse(
            LocalDate startDate,
            LocalDate endDate,
            long orderCount,
            double orderCountChangeRate,
            BigDecimal orderAmount,
            double orderAmountChangeRate,
            BigDecimal averageOrderAmount,
            double averageOrderAmountChangeRate,
            double paymentApprovalRate,
            double paymentApprovalRateChange,
            BigDecimal settlementDifferenceAmount,
            double settlementDifferenceChangeRate,
            int settlementMismatchCount
    ) {
    }

    /** 일별 × 채널 × 매장 주문 집계. */
    public record OrderAnalyticsRow(
            LocalDate date,
            String channel,
            Long storeId,
            String storeName,
            long orderCount,
            BigDecimal orderAmount,
            BigDecimal averageOrderAmount,
            long cancelCount,
            BigDecimal cancelAmount
    ) {
    }

    /** 일별 × 결제수단 결제 집계. 승인율은 프론트/DAX 에서 계산하도록 원본 건수를 함께 제공. */
    public record PaymentAnalyticsRow(
            LocalDate date,
            String paymentMethod,
            long paymentRequestCount,
            long approvalCount,
            BigDecimal approvalAmount,
            long cancelCount,
            BigDecimal cancelAmount,
            long failureCount
    ) {
    }

    /** 정산 명세/대사 상세. differenceAmount = approvalAmount − settlementAmount. */
    public record SettlementAnalyticsRow(
            LocalDate settlementDate,
            String orderNo,
            String tid,
            Long storeId,
            String storeName,
            String paymentMethod,
            BigDecimal approvalAmount,
            BigDecimal settlementAmount,
            BigDecimal differenceAmount,
            String settlementStatus,
            boolean isMismatch
    ) {
    }

    /** SKU 단위 재고 현황 + 최근 30일 판매량 + 입고 예정. */
    public record InventoryAnalyticsRow(
            Long productId,
            String productName,
            String optionName,
            String sku,
            String category,
            Long storeId,
            String storeName,
            int currentStock,
            int safetyStock,
            long soldLast30Days,
            Integer incomingQuantity,
            LocalDate incomingDate,
            boolean isLowStock
    ) {
    }

    /** 분석 화면(프론트) 전용 묶음 — 운영 개요 한 번의 호출로 필요한 하위 데이터까지. */
    public record OverviewBundle(
            OverviewResponse kpi,
            List<OrderAnalyticsRow> orders,
            List<PaymentAnalyticsRow> payments,
            List<SettlementAnalyticsRow> settlementMismatches,
            List<InventoryAnalyticsRow> lowStock
    ) {
    }
}
