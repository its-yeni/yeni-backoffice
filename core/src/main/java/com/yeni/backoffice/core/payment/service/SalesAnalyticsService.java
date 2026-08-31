package com.yeni.backoffice.core.payment.service;

import com.yeni.backoffice.core.commerce.entity.CommerceOrder;
import com.yeni.backoffice.core.commerce.repository.CommerceOrderRepository;
import com.yeni.backoffice.core.payment.dto.SalesAnalyticsDtos.CategorySalesRow;
import com.yeni.backoffice.core.payment.dto.SalesAnalyticsDtos.DailySalesPoint;
import com.yeni.backoffice.core.payment.dto.SalesAnalyticsDtos.LedgerConsistencyIssue;
import com.yeni.backoffice.core.payment.dto.SalesAnalyticsDtos.LedgerConsistencyReport;
import com.yeni.backoffice.core.payment.dto.SalesAnalyticsDtos.ProductSalesRow;
import com.yeni.backoffice.core.payment.dto.SalesAnalyticsDtos.SalesBreakdownResponse;
import com.yeni.backoffice.core.payment.dto.SalesAnalyticsDtos.SalesSummaryResponse;
import com.yeni.backoffice.core.payment.entity.SalesTransaction;
import com.yeni.backoffice.core.payment.entity.SalesTransactionLine;
import com.yeni.backoffice.core.payment.entity.SettlementDetail;
import com.yeni.backoffice.core.payment.entity.SettlementStatement;
import com.yeni.backoffice.core.payment.enums.SaleType;
import com.yeni.backoffice.core.payment.repository.SalesTransactionLineRepository;
import com.yeni.backoffice.core.payment.repository.SalesTransactionRepository;
import com.yeni.backoffice.core.payment.repository.SettlementDetailRepository;
import com.yeni.backoffice.core.payment.repository.SettlementStatementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 매출 명세 라인({@link SalesTransactionLine}) 기반의 분류별/상품별 집계와,
 * 주문 → 매출 헤더 → 매출 라인 → 정산 명세 사이의 금액 항등식 검산 리포트를 제공한다.
 */
@Service
public class SalesAnalyticsService {

    private final SalesTransactionRepository salesRepository;
    private final SalesTransactionLineRepository salesLineRepository;
    private final CommerceOrderRepository orderRepository;
    private final SettlementStatementRepository settlementStatementRepository;
    private final SettlementDetailRepository settlementDetailRepository;

    public SalesAnalyticsService(
            SalesTransactionRepository salesRepository,
            SalesTransactionLineRepository salesLineRepository,
            CommerceOrderRepository orderRepository,
            SettlementStatementRepository settlementStatementRepository,
            SettlementDetailRepository settlementDetailRepository) {
        this.salesRepository = salesRepository;
        this.salesLineRepository = salesLineRepository;
        this.orderRepository = orderRepository;
        this.settlementStatementRepository = settlementStatementRepository;
        this.settlementDetailRepository = settlementDetailRepository;
    }

    @Transactional(readOnly = true)
    public SalesBreakdownResponse breakdown(LocalDate startDate, LocalDate endDate, Long storeId, Boolean confirmedYn) {
        LocalDate start = startDate == null ? LocalDate.now().minusDays(30) : startDate;
        LocalDate end = endDate == null ? LocalDate.now() : endDate;

        List<Object[]> categoryRows = salesLineRepository.aggregateByCategory(start, end, storeId, confirmedYn);
        BigDecimal net = categoryRows.stream()
                .map(r -> (BigDecimal) r[2])
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal denominator = net.signum() == 0 ? BigDecimal.ONE : net;

        List<CategorySalesRow> categories = categoryRows.stream()
                .map(r -> new CategorySalesRow(
                        (String) r[0],
                        ((Number) r[1]).longValue(),
                        (BigDecimal) r[2],
                        ((BigDecimal) r[2]).multiply(BigDecimal.valueOf(100)).divide(denominator, 1, RoundingMode.HALF_UP),
                        ((Number) r[3]).longValue()))
                .toList();

        List<ProductSalesRow> products = salesLineRepository.aggregateByProduct(start, end, storeId, confirmedYn).stream()
                .map(r -> new ProductSalesRow(
                        r[0] == null ? null : ((Number) r[0]).longValue(),
                        (String) r[1],
                        ((Number) r[2]).longValue(),
                        (BigDecimal) r[3],
                        ((Number) r[4]).longValue()))
                .toList();

        return new SalesBreakdownResponse(start, end, storeId, confirmedYn, net, categories, products);
    }

    /**
     * 매출 분석 상단 요약(총 순매출·주문건수·평균 주문금액·취소율)과 일자별 매출 추이.
     * 분류별 도넛과 같은 매출 명세 라인({@link SalesTransactionLine})을 근거로 하므로
     * 상단 지표와 하단 분류/상품 집계의 금액이 서로 어긋나지 않는다.
     */
    @Transactional(readOnly = true)
    public SalesSummaryResponse summary(LocalDate startDate, LocalDate endDate, Long storeId, Boolean confirmedYn) {
        LocalDate start = startDate == null ? LocalDate.now().minusDays(30) : startDate;
        LocalDate end = endDate == null ? LocalDate.now() : endDate;

        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal cancelSigned = BigDecimal.ZERO;
        long orders = 0;
        List<Object[]> agg = salesLineRepository.summarize(start, end, storeId, confirmedYn);
        if (!agg.isEmpty() && agg.get(0) != null) {
            Object[] row = agg.get(0);
            gross = row[0] == null ? BigDecimal.ZERO : (BigDecimal) row[0];
            cancelSigned = row[1] == null ? BigDecimal.ZERO : (BigDecimal) row[1];
            orders = row[2] == null ? 0L : ((Number) row[2]).longValue();
        }
        BigDecimal cancelAmount = cancelSigned.abs();
        BigDecimal net = gross.add(cancelSigned);
        BigDecimal avgOrder = orders == 0
                ? BigDecimal.ZERO
                : net.divide(BigDecimal.valueOf(orders), 0, RoundingMode.HALF_UP);
        BigDecimal cancelRate = gross.signum() == 0
                ? BigDecimal.ZERO
                : cancelAmount.multiply(BigDecimal.valueOf(100)).divide(gross, 1, RoundingMode.HALF_UP);

        List<DailySalesPoint> daily = salesLineRepository.aggregateDaily(start, end, storeId, confirmedYn).stream()
                .map(row -> {
                    BigDecimal sale = row[1] == null ? BigDecimal.ZERO : (BigDecimal) row[1];
                    BigDecimal cancel = row[2] == null ? BigDecimal.ZERO : (BigDecimal) row[2];
                    long dayOrders = row[3] == null ? 0L : ((Number) row[3]).longValue();
                    return new DailySalesPoint((LocalDate) row[0], sale, cancel.abs(), sale.add(cancel), dayOrders);
                })
                .toList();

        return new SalesSummaryResponse(start, end, gross, cancelAmount, net, orders, avgOrder, cancelRate, daily);
    }

    @Transactional(readOnly = true)
    public LedgerConsistencyReport consistencyReport(LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate == null ? LocalDate.now().minusDays(30) : startDate;
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        List<LedgerConsistencyIssue> issues = new ArrayList<>();

        List<SalesTransaction> headers = salesRepository.findByBusinessDateBetweenOrderByOccurredAtDesc(start, end);
        Map<Long, List<SalesTransactionLine>> linesByHeader = salesLineRepository
                .findBySalesTransactionIdInOrderByIdAsc(headers.stream().map(SalesTransaction::getId).toList())
                .stream().collect(Collectors.groupingBy(SalesTransactionLine::getSalesTransactionId));

        // 1) 매출 헤더 금액 = 라인 합계
        int checkedHeaders = 0;
        for (SalesTransaction header : headers) {
            List<SalesTransactionLine> lines = linesByHeader.get(header.getId());
            if (lines == null || lines.isEmpty()) continue;
            checkedHeaders++;
            BigDecimal lineSum = lines.stream().map(SalesTransactionLine::getLineAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (lineSum.compareTo(header.getSaleAmount()) != 0) {
                issues.add(new LedgerConsistencyIssue("SALES_HEADER", "sales#" + header.getId(),
                        "매출 헤더 금액과 명세 라인 합계가 다릅니다.", header.getSaleAmount(), lineSum,
                        lineSum.subtract(header.getSaleAmount())));
            }
        }

        // 2) 주문 결제금액 = SALE 헤더 합계, 주문 취소금액 = CANCEL 헤더 합계(절대값)
        Map<String, List<SalesTransaction>> byOrderNo = headers.stream()
                .collect(Collectors.groupingBy(SalesTransaction::getOrderNo));
        int checkedOrders = 0;
        for (Map.Entry<String, List<SalesTransaction>> entry : byOrderNo.entrySet()) {
            CommerceOrder order = orderRepository.findByOrderNo(entry.getKey()).orElse(null);
            if (order == null) continue;
            checkedOrders++;
            BigDecimal saleSum = entry.getValue().stream()
                    .filter(s -> SaleType.SALE.equals(s.getSaleType()))
                    .map(SalesTransaction::getSaleAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal cancelSum = entry.getValue().stream()
                    .filter(s -> SaleType.CANCEL.equals(s.getSaleType()))
                    .map(s -> s.getSaleAmount().abs()).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (saleSum.signum() > 0 && saleSum.compareTo(order.getPayableAmount()) != 0) {
                issues.add(new LedgerConsistencyIssue("ORDER_SALE", order.getOrderNo(),
                        "주문 결제금액과 SALE 매출 합계가 다릅니다.", order.getPayableAmount(), saleSum,
                        saleSum.subtract(order.getPayableAmount())));
            }
            if (order.getCancelledAmount() != null && order.getCancelledAmount().signum() > 0
                    && cancelSum.compareTo(order.getCancelledAmount()) != 0) {
                issues.add(new LedgerConsistencyIssue("ORDER_CANCEL", order.getOrderNo(),
                        "주문 취소금액과 CANCEL 매출 합계가 다릅니다.", order.getCancelledAmount(), cancelSum,
                        cancelSum.subtract(order.getCancelledAmount())));
            }
        }

        // 3) 정산서 gross = 정산 명세 합계
        List<SettlementStatement> statements = settlementStatementRepository
                .findBySettlementDateBetweenOrderBySettlementDateDesc(start, end);
        for (SettlementStatement statement : statements) {
            List<SettlementDetail> details = settlementDetailRepository
                    .findBySettlementStatementIdOrderByIdAsc(statement.getId());
            BigDecimal detailSum = details.stream().map(SettlementDetail::getSaleAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (statement.getGrossAmount().compareTo(detailSum) != 0) {
                issues.add(new LedgerConsistencyIssue("SETTLEMENT", "statement#" + statement.getId(),
                        "정산서 총매출과 정산 명세 합계가 다릅니다.", statement.getGrossAmount(), detailSum,
                        detailSum.subtract(statement.getGrossAmount())));
            }
        }

        return new LedgerConsistencyReport(start, end, checkedHeaders, checkedOrders, statements.size(), issues);
    }
}
