package com.yeni.backoffice.core.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 매출 분석(분류별/상품별 집계)과 금액 정합성 검산 리포트 응답. */
public final class SalesAnalyticsDtos {

    private SalesAnalyticsDtos() {
    }

    public record CategorySalesRow(
            String categoryName,
            long quantity,
            BigDecimal netAmount,
            BigDecimal share,
            long lineCount
    ) {
    }

    public record ProductSalesRow(
            Long productId,
            String productName,
            long quantity,
            BigDecimal netAmount,
            long lineCount
    ) {
    }

    public record SalesBreakdownResponse(
            LocalDate startDate,
            LocalDate endDate,
            Long storeId,
            Boolean confirmedYn,
            BigDecimal netAmount,
            List<CategorySalesRow> categories,
            List<ProductSalesRow> products
    ) {
    }

    /** 매출 분석 상단 요약 지표 + 일자별 매출 추이. */
    public record DailySalesPoint(
            LocalDate date,
            BigDecimal saleAmount,
            BigDecimal cancelAmount,
            BigDecimal netAmount,
            long orderCount
    ) {
    }

    public record SalesSummaryResponse(
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal grossSales,
            BigDecimal cancelAmount,
            BigDecimal netSales,
            long orderCount,
            BigDecimal avgOrderAmount,
            BigDecimal cancelRate,
            List<DailySalesPoint> daily
    ) {
    }

    public record PendingSalesRow(
            Long salesId,
            String orderNo,
            java.time.LocalDateTime occurredAt,
            Long paymentId,
            String tid,
            String storeName,
            String categoryNames,
            String productSummary,
            int itemCount,
            java.math.BigDecimal saleAmount,
            String settlementStatus,
            String deliveryStatus,
            boolean confirmable
    ) {
    }

    public record PendingSalesResponse(
            LocalDate startDate,
            LocalDate endDate,
            java.math.BigDecimal totalAmount,
            int count,
            List<PendingSalesRow> rows
    ) {
    }

    public record StoreSettlementSummary(
            Long storeId,
            String storeName,
            int statementCount,
            java.math.BigDecimal grossAmount,
            java.math.BigDecimal feeAmount,
            java.math.BigDecimal vatAmount,
            java.math.BigDecimal netAmount,
            java.math.BigDecimal paidAmount,
            int draftCount,
            int confirmedCount,
            int paidCount
    ) {
    }

    public record SettlementCategoryRow(
            String categoryName,
            BigDecimal saleAmount,
            BigDecimal feeAmount,
            BigDecimal netAmount
    ) {
    }

    public record LedgerConsistencyIssue(
            String scope,
            String reference,
            String description,
            BigDecimal expected,
            BigDecimal actual,
            BigDecimal difference
    ) {
    }

    public record LedgerConsistencyReport(
            LocalDate startDate,
            LocalDate endDate,
            int checkedHeaders,
            int checkedOrders,
            int checkedStatements,
            List<LedgerConsistencyIssue> issues
    ) {
        public boolean balanced() {
            return issues.isEmpty();
        }
    }
}
