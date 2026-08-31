package com.yeni.backoffice.core.payment.service;

import com.yeni.backoffice.core.commerce.repository.CommerceOrderRepository;
import com.yeni.backoffice.core.payment.dto.SalesAnalyticsDtos.LedgerConsistencyReport;
import com.yeni.backoffice.core.payment.entity.SalesTransaction;
import com.yeni.backoffice.core.payment.entity.SalesTransactionLine;
import com.yeni.backoffice.core.payment.enums.SaleType;
import com.yeni.backoffice.core.payment.repository.SalesTransactionLineRepository;
import com.yeni.backoffice.core.payment.repository.SalesTransactionRepository;
import com.yeni.backoffice.core.payment.repository.SettlementDetailRepository;
import com.yeni.backoffice.core.payment.repository.SettlementStatementRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SalesAnalyticsServiceTest {

    private final SalesTransactionRepository salesRepository = mock(SalesTransactionRepository.class);
    private final SalesTransactionLineRepository lineRepository = mock(SalesTransactionLineRepository.class);
    private final CommerceOrderRepository orderRepository = mock(CommerceOrderRepository.class);
    private final SettlementStatementRepository statementRepository = mock(SettlementStatementRepository.class);
    private final SettlementDetailRepository detailRepository = mock(SettlementDetailRepository.class);

    private final SalesAnalyticsService service = new SalesAnalyticsService(
            salesRepository, lineRepository, orderRepository, statementRepository, detailRepository);

    private SalesTransaction saleHeader(long id, String amount) {
        return SalesTransaction.builder()
                .id(id).orderNo("O" + id).saleType(SaleType.SALE)
                .saleAmount(new BigDecimal(amount)).businessDate(LocalDate.now()).build();
    }

    private SalesTransactionLine line(long headerId, String amount) {
        return SalesTransactionLine.builder()
                .salesTransactionId(headerId).saleType(SaleType.SALE).categoryName("피자")
                .quantity(1).lineAmount(new BigDecimal(amount))
                .supplyAmount(BigDecimal.ZERO).vatAmount(BigDecimal.ZERO)
                .businessDate(LocalDate.now()).occurredAt(LocalDateTime.now()).build();
    }

    @Test
    void consistencyReport_flagsHeaderWhoseLinesDoNotSumToSaleAmount() {
        when(salesRepository.findByBusinessDateBetweenOrderByOccurredAtDesc(any(), any()))
                .thenReturn(List.of(saleHeader(1, "10000")));
        when(lineRepository.findBySalesTransactionIdInOrderByIdAsc(any()))
                .thenReturn(List.of(line(1, "6000"), line(1, "3000"))); // sums 9000 ≠ 10000
        when(orderRepository.findByOrderNo(any())).thenReturn(java.util.Optional.empty());
        when(statementRepository.findBySettlementDateBetweenOrderBySettlementDateDesc(any(), any()))
                .thenReturn(List.of());

        LedgerConsistencyReport report = service.consistencyReport(LocalDate.now().minusDays(7), LocalDate.now());

        assertThat(report.balanced()).isFalse();
        assertThat(report.issues()).hasSize(1);
        assertThat(report.issues().get(0).scope()).isEqualTo("SALES_HEADER");
        assertThat(report.issues().get(0).difference()).isEqualByComparingTo("-1000");
    }

    @Test
    void summary_computesNetOrdersAvgAndCancelRateFromLines() {
        // SALE 라인 합 12000, CANCEL 라인 합 -2000 → 순매출 10000, 취소 2000, 취소율 16.7%, 주문 4건
        when(lineRepository.summarize(any(), any(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{new BigDecimal("12000"), new BigDecimal("-2000"), 4L}));
        when(lineRepository.aggregateDaily(any(), any(), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{LocalDate.now().minusDays(1), new BigDecimal("5000"), BigDecimal.ZERO, 2L},
                        new Object[]{LocalDate.now(), new BigDecimal("7000"), new BigDecimal("-2000"), 2L}));

        var summary = service.summary(LocalDate.now().minusDays(7), LocalDate.now(), null, null);

        assertThat(summary.grossSales()).isEqualByComparingTo("12000");
        assertThat(summary.cancelAmount()).isEqualByComparingTo("2000");
        assertThat(summary.netSales()).isEqualByComparingTo("10000");
        assertThat(summary.orderCount()).isEqualTo(4);
        assertThat(summary.avgOrderAmount()).isEqualByComparingTo("2500");
        assertThat(summary.cancelRate()).isEqualByComparingTo("16.7");
        assertThat(summary.daily()).hasSize(2);
        assertThat(summary.daily().get(1).netAmount()).isEqualByComparingTo("5000");
        assertThat(summary.daily().get(1).cancelAmount()).isEqualByComparingTo("2000");
        assertThat(summary.daily().get(1).orderCount()).isEqualTo(2);
    }

    @Test
    void summary_returnsZeroesWhenNoLines() {
        when(lineRepository.summarize(any(), any(), any(), any())).thenReturn(List.of());
        when(lineRepository.aggregateDaily(any(), any(), any(), any())).thenReturn(List.of());

        var summary = service.summary(null, null, null, null);

        assertThat(summary.netSales()).isEqualByComparingTo("0");
        assertThat(summary.orderCount()).isZero();
        assertThat(summary.avgOrderAmount()).isEqualByComparingTo("0");
        assertThat(summary.cancelRate()).isEqualByComparingTo("0");
        assertThat(summary.daily()).isEmpty();
    }

    @Test
    void consistencyReport_balancedWhenLinesSumToHeader() {
        when(salesRepository.findByBusinessDateBetweenOrderByOccurredAtDesc(any(), any()))
                .thenReturn(List.of(saleHeader(1, "10000")));
        when(lineRepository.findBySalesTransactionIdInOrderByIdAsc(any()))
                .thenReturn(List.of(line(1, "7000"), line(1, "3000")));
        when(orderRepository.findByOrderNo(any())).thenReturn(java.util.Optional.empty());
        when(statementRepository.findBySettlementDateBetweenOrderBySettlementDateDesc(any(), any()))
                .thenReturn(List.of());

        LedgerConsistencyReport report = service.consistencyReport(LocalDate.now().minusDays(7), LocalDate.now());

        assertThat(report.balanced()).isTrue();
        assertThat(report.checkedHeaders()).isEqualTo(1);
    }
}
