package com.yeni.backoffice.core.analytics.service;

import com.yeni.backoffice.core.analytics.dto.AnalyticsDtos.AnalyticsFilter;
import com.yeni.backoffice.core.analytics.dto.AnalyticsDtos.InventoryAnalyticsRow;
import com.yeni.backoffice.core.analytics.dto.AnalyticsDtos.OrderAnalyticsRow;
import com.yeni.backoffice.core.analytics.dto.AnalyticsDtos.OverviewBundle;
import com.yeni.backoffice.core.analytics.dto.AnalyticsDtos.OverviewResponse;
import com.yeni.backoffice.core.analytics.dto.AnalyticsDtos.PaymentAnalyticsRow;
import com.yeni.backoffice.core.analytics.dto.AnalyticsDtos.SettlementAnalyticsRow;
import com.yeni.backoffice.core.commerce.entity.CommerceOrder;
import com.yeni.backoffice.core.commerce.entity.CommerceStore;
import com.yeni.backoffice.core.commerce.enums.InventoryTransactionType;
import com.yeni.backoffice.core.commerce.enums.OrderStatus;
import com.yeni.backoffice.core.commerce.enums.StoreBusinessType;
import com.yeni.backoffice.core.commerce.repository.CommerceOrderRepository;
import com.yeni.backoffice.core.commerce.repository.CommerceStoreRepository;
import com.yeni.backoffice.core.commerce.repository.InventoryTransactionRepository;
import com.yeni.backoffice.core.commerce.service.LocationInventoryService;
import com.yeni.backoffice.core.payment.entity.PaymentTransaction;
import com.yeni.backoffice.core.payment.entity.PgSettlementImport;
import com.yeni.backoffice.core.payment.entity.PgSettlementReconciliation;
import com.yeni.backoffice.core.payment.entity.SalesTransaction;
import com.yeni.backoffice.core.payment.entity.SettlementDetail;
import com.yeni.backoffice.core.payment.entity.SettlementStatement;
import com.yeni.backoffice.core.payment.enums.PaymentStatus;
import com.yeni.backoffice.core.payment.enums.PgReconciliationStatus;
import com.yeni.backoffice.core.payment.enums.SaleType;
import com.yeni.backoffice.core.payment.repository.PaymentTransactionRepository;
import com.yeni.backoffice.core.payment.repository.PgSettlementImportRepository;
import com.yeni.backoffice.core.payment.repository.PgSettlementReconciliationRepository;
import com.yeni.backoffice.core.payment.repository.SalesTransactionRepository;
import com.yeni.backoffice.core.payment.repository.SettlementDetailRepository;
import com.yeni.backoffice.core.payment.repository.SettlementStatementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Power BI 연동을 고려한 <b>읽기 전용</b> 분석 집계 서비스.
 * 업무 로직은 재구현하지 않고 기존 Repository / Service 를 재사용해 flat 한 행 집합으로 변환만 한다.
 * 데이터 규모(포트폴리오 데모)를 감안해 in-memory 스트림 집계로 처리한다.
 */
@Service
public class AnalyticsService {

    private static final Set<PaymentStatus> APPROVED_STATUSES =
            EnumSet.of(PaymentStatus.APPROVED, PaymentStatus.PARTIAL_CANCELED, PaymentStatus.CANCELED);
    private static final int SHIPMENT_WINDOW_DAYS = 30;

    private final CommerceOrderRepository orders;
    private final CommerceStoreRepository stores;
    private final PaymentTransactionRepository payments;
    private final SalesTransactionRepository sales;
    private final SettlementStatementRepository statements;
    private final SettlementDetailRepository settlementDetails;
    private final PgSettlementReconciliationRepository reconciliations;
    private final PgSettlementImportRepository settlementImports;
    private final LocationInventoryService locationInventory;
    private final InventoryTransactionRepository inventoryTransactions;

    public AnalyticsService(CommerceOrderRepository orders, CommerceStoreRepository stores,
            PaymentTransactionRepository payments, SalesTransactionRepository sales,
            SettlementStatementRepository statements, SettlementDetailRepository settlementDetails,
            PgSettlementReconciliationRepository reconciliations, PgSettlementImportRepository settlementImports,
            LocationInventoryService locationInventory, InventoryTransactionRepository inventoryTransactions) {
        this.orders = orders;
        this.stores = stores;
        this.payments = payments;
        this.sales = sales;
        this.statements = statements;
        this.settlementDetails = settlementDetails;
        this.reconciliations = reconciliations;
        this.settlementImports = settlementImports;
        this.locationInventory = locationInventory;
        this.inventoryTransactions = inventoryTransactions;
    }

    /* ─────────────────────────── 채널(파생) ─────────────────────────── */

    /** 채널 필드는 데이터 모델에 없어 매장 업태로 파생한다. */
    public static String channelOf(StoreBusinessType type) {
        if (type == StoreBusinessType.ONLINE_RETAIL) return "자사몰";
        if (type == StoreBusinessType.FOOD_SERVICE) return "매장";
        return "기타";
    }

    private boolean channelMatches(AnalyticsFilter filter, String channel) {
        return filter.channel() == null || filter.channel().equalsIgnoreCase(channel);
    }

    private Map<Long, CommerceStore> storeMap() {
        return stores.findAll().stream().collect(Collectors.toMap(CommerceStore::getId, Function.identity()));
    }

    /* ─────────────────────────── 주문 ─────────────────────────── */

    @Transactional(readOnly = true)
    public List<OrderAnalyticsRow> orders(AnalyticsFilter filter) {
        Map<Long, CommerceStore> storeById = storeMap();
        record Key(LocalDate date, Long storeId) {
        }
        Map<Key, long[]> counts = new LinkedHashMap<>();
        Map<Key, BigDecimal[]> amounts = new LinkedHashMap<>();

        for (CommerceOrder order : orders.findAll()) {
            if (order.getCreatedAt() == null) continue;
            LocalDate date = order.getCreatedAt().toLocalDate();
            if (!filter.matchesDate(date)) continue;
            if (filter.storeId() != null && !filter.storeId().equals(order.getStoreId())) continue;
            CommerceStore store = storeById.get(order.getStoreId());
            String channel = store == null ? "기타" : channelOf(store.getBusinessType());
            if (!channelMatches(filter, channel)) continue;

            Key key = new Key(date, order.getStoreId());
            long[] c = counts.computeIfAbsent(key, k -> new long[2]);          // [orderCount, cancelCount]
            BigDecimal[] a = amounts.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO}); // [orderAmount, cancelAmount]
            c[0]++;
            a[0] = a[0].add(nz(order.getPayableAmount()));
            boolean cancelled = order.getOrderStatus() == OrderStatus.CANCELLED
                    || order.getOrderStatus() == OrderStatus.PARTIALLY_CANCELLED;
            if (cancelled) {
                c[1]++;
                a[1] = a[1].add(nz(order.getCancelledAmount()));
            }
        }

        List<OrderAnalyticsRow> rows = new ArrayList<>();
        counts.forEach((key, c) -> {
            BigDecimal[] a = amounts.get(key);
            CommerceStore store = storeById.get(key.storeId());
            String channel = store == null ? "기타" : channelOf(store.getBusinessType());
            String storeName = store == null ? "-" : store.getStoreName();
            BigDecimal avg = c[0] == 0 ? BigDecimal.ZERO
                    : a[0].divide(BigDecimal.valueOf(c[0]), 0, RoundingMode.HALF_UP);
            rows.add(new OrderAnalyticsRow(key.date(), channel, key.storeId(), storeName,
                    c[0], a[0], avg, c[1], a[1]));
        });
        rows.sort(Comparator.comparing(OrderAnalyticsRow::date).thenComparing(r -> nvl(r.storeName())));
        return rows;
    }

    /* ─────────────────────────── 결제 ─────────────────────────── */

    @Transactional(readOnly = true)
    public List<PaymentAnalyticsRow> payments(AnalyticsFilter filter) {
        Map<String, String> methodByOrderNo = sales.findByBusinessDateBetweenOrderByOccurredAtDesc(
                        filter.startDate().minusDays(3), filter.endDate().plusDays(3)).stream()
                .filter(s -> s.getSaleType() == SaleType.SALE && s.getPaymentMethod() != null)
                .collect(Collectors.toMap(SalesTransaction::getOrderNo, s -> s.getPaymentMethod(), (a, b) -> a));

        record Key(LocalDate date, String method) {
        }
        Map<Key, long[]> counts = new LinkedHashMap<>();          // [request, approval, cancel, failure]
        Map<Key, BigDecimal[]> amounts = new LinkedHashMap<>();   // [approvalAmount, cancelAmount]

        for (PaymentTransaction tx : payments.findAll()) {
            LocalDateTime at = tx.getApprovedAt() != null ? tx.getApprovedAt() : tx.getCreatedAt();
            if (at == null) continue;
            LocalDate date = at.toLocalDate();
            if (!filter.matchesDate(date)) continue;
            if (filter.storeId() != null && !filter.storeId().equals(tx.getStoreId())) continue;
            String method = methodByOrderNo.getOrDefault(tx.getOrderNo(), "기타");
            if (filter.paymentMethod() != null && !filter.paymentMethod().equalsIgnoreCase(method)) continue;

            Key key = new Key(date, method);
            long[] c = counts.computeIfAbsent(key, k -> new long[4]);
            BigDecimal[] a = amounts.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            c[0]++;
            if (APPROVED_STATUSES.contains(tx.getPaymentStatus())) {
                c[1]++;
                a[0] = a[0].add(nz(tx.getApprovedAmount()));
            }
            if (nz(tx.getCanceledAmount()).signum() > 0) {
                c[2]++;
                a[1] = a[1].add(nz(tx.getCanceledAmount()));
            }
            if (tx.getPaymentStatus() == PaymentStatus.APPROVE_FAILED) c[3]++;
        }

        List<PaymentAnalyticsRow> rows = new ArrayList<>();
        counts.forEach((key, c) -> {
            BigDecimal[] a = amounts.get(key);
            rows.add(new PaymentAnalyticsRow(key.date(), key.method(),
                    c[0], c[1], a[0], c[2], a[1], c[3]));
        });
        rows.sort(Comparator.comparing(PaymentAnalyticsRow::date).thenComparing(PaymentAnalyticsRow::paymentMethod));
        return rows;
    }

    /* ─────────────────────────── 정산 ─────────────────────────── */

    @Transactional(readOnly = true)
    public List<SettlementAnalyticsRow> settlements(AnalyticsFilter filter) {
        Map<Long, LocalDate> importDate = settlementImports.findAll().stream()
                .collect(Collectors.toMap(PgSettlementImport::getId, PgSettlementImport::getBusinessDate, (a, b) -> a));
        Map<Long, SalesTransaction> salesById = sales.findAll().stream()
                .collect(Collectors.toMap(SalesTransaction::getId, Function.identity(), (a, b) -> a));
        Map<Long, CommerceStore> storeById = storeMap();
        Map<Long, SettlementStatement> statementById = statements.findAll().stream()
                .collect(Collectors.toMap(SettlementStatement::getId, Function.identity(), (a, b) -> a));
        Map<Long, Long> statementIdBySalesId = settlementDetails.findAll().stream()
                .collect(Collectors.toMap(SettlementDetail::getSalesId, SettlementDetail::getSettlementStatementId, (a, b) -> a));

        List<SettlementAnalyticsRow> rows = new ArrayList<>();
        for (PgSettlementReconciliation r : reconciliations.findAll()) {
            LocalDate date = importDate.get(r.getImportId());
            SalesTransaction s = r.getSalesTransactionId() == null ? null : salesById.get(r.getSalesTransactionId());
            if (date == null && s != null) date = s.getBusinessDate();
            if (date == null || !filter.matchesDate(date)) continue;

            Long storeId = s != null ? s.getStoreId() : null;
            if (filter.storeId() != null && !filter.storeId().equals(storeId)) continue;
            CommerceStore store = storeId == null ? null : storeById.get(storeId);
            String method = s != null && s.getPaymentMethod() != null ? s.getPaymentMethod() : "기타";
            if (filter.paymentMethod() != null && !filter.paymentMethod().equalsIgnoreCase(method)) continue;

            BigDecimal approval = nz(r.getInternalAmount());
            BigDecimal settlement = nz(r.getPgAmount());
            BigDecimal diff = approval.subtract(settlement);
            boolean mismatch = r.getStatus() != PgReconciliationStatus.MATCHED;

            String status = "미정산";
            if (s != null) {
                Long stId = statementIdBySalesId.get(s.getId());
                SettlementStatement st = stId == null ? null : statementById.get(stId);
                if (st != null) status = st.getSettlementStatus().name();
            }

            rows.add(new SettlementAnalyticsRow(date, r.getOrderNo(), r.getTid(),
                    storeId, store == null ? "-" : store.getStoreName(), method,
                    approval, settlement, diff, status, mismatch));
        }
        rows.sort(Comparator.comparing(SettlementAnalyticsRow::settlementDate).reversed()
                .thenComparing(r -> nvl(r.orderNo())));
        return rows;
    }

    /* ─────────────────────────── 재고 ─────────────────────────── */

    @Transactional(readOnly = true)
    public List<InventoryAnalyticsRow> inventory(AnalyticsFilter filter) {
        Map<Long, Integer> shippedByVariant = inventoryTransactions
                .findByTypeAndCreatedAtGreaterThanEqual(InventoryTransactionType.SHIPMENT,
                        LocalDateTime.now().minusDays(SHIPMENT_WINDOW_DAYS))
                .stream().collect(Collectors.groupingBy(t -> t.getVariantId(),
                        Collectors.summingInt(t -> t.getQuantity())));

        return locationInventory.list(filter.storeId()).stream()
                .map(row -> {
                    int sold = shippedByVariant.getOrDefault(row.variantId(), 0);
                    Integer incoming = row.incomingQuantity() > 0 ? row.incomingQuantity() : null;
                    boolean low = row.stockQuantity() < row.safetyStock();
                    return new InventoryAnalyticsRow(row.productId(), row.productName(),
                            optionName(row.optionSummary()), row.sku(), row.category(),
                            row.storeId(), row.storeName(),
                            row.stockQuantity(), row.safetyStock(), sold,
                            incoming, null, low);
                })
                .sorted(Comparator.comparingInt((InventoryAnalyticsRow r) -> r.isLowStock() ? 0 : 1)
                        .thenComparing(r -> r.currentStock())
                        .thenComparing(r -> nvl(r.productName())))
                .toList();
    }

    private String optionName(String optionSummary) {
        return optionSummary == null || optionSummary.isBlank() || "BASE".equals(optionSummary) ? "기본" : optionSummary;
    }

    /* ─────────────────────────── 운영 개요 ─────────────────────────── */

    @Transactional(readOnly = true)
    public OverviewResponse overview(AnalyticsFilter filter) {
        return overview(filter, orders(filter), orders(filter.previousPeriod()),
                paymentApproval(filter), paymentApproval(filter.previousPeriod()),
                settlements(filter), settlements(filter.previousPeriod()));
    }

    @Transactional(readOnly = true)
    public OverviewBundle overviewBundle(AnalyticsFilter filter) {
        List<OrderAnalyticsRow> currentOrders = orders(filter);
        List<PaymentAnalyticsRow> currentPayments = payments(filter);
        List<SettlementAnalyticsRow> currentSettlements = settlements(filter);
        List<InventoryAnalyticsRow> lowStock = inventory(filter).stream()
                .filter(InventoryAnalyticsRow::isLowStock).limit(5).toList();
        OverviewResponse kpi = overview(filter, currentOrders, orders(filter.previousPeriod()),
                paymentApproval(filter), paymentApproval(filter.previousPeriod()),
                currentSettlements, settlements(filter.previousPeriod()));
        List<SettlementAnalyticsRow> mismatches = currentSettlements.stream()
                .filter(SettlementAnalyticsRow::isMismatch).limit(5).toList();
        return new OverviewBundle(kpi, currentOrders, currentPayments, mismatches, lowStock);
    }

    private OverviewResponse overview(AnalyticsFilter filter,
            List<OrderAnalyticsRow> current, List<OrderAnalyticsRow> previous,
            long[] approvalNow, long[] approvalPrev,
            List<SettlementAnalyticsRow> settlementNow, List<SettlementAnalyticsRow> settlementPrev) {

        long orderCount = current.stream().mapToLong(OrderAnalyticsRow::orderCount).sum();
        long prevOrderCount = previous.stream().mapToLong(OrderAnalyticsRow::orderCount).sum();
        BigDecimal orderAmount = current.stream().map(OrderAnalyticsRow::orderAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal prevOrderAmount = previous.stream().map(OrderAnalyticsRow::orderAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = orderCount == 0 ? BigDecimal.ZERO
                : orderAmount.divide(BigDecimal.valueOf(orderCount), 0, RoundingMode.HALF_UP);
        BigDecimal prevAvg = prevOrderCount == 0 ? BigDecimal.ZERO
                : prevOrderAmount.divide(BigDecimal.valueOf(prevOrderCount), 0, RoundingMode.HALF_UP);

        double approvalRate = rate(approvalNow[1], approvalNow[0]);
        double prevApprovalRate = rate(approvalPrev[1], approvalPrev[0]);

        BigDecimal diffNow = settlementNow.stream().filter(SettlementAnalyticsRow::isMismatch)
                .map(r -> r.differenceAmount().abs()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal diffPrev = settlementPrev.stream().filter(SettlementAnalyticsRow::isMismatch)
                .map(r -> r.differenceAmount().abs()).reduce(BigDecimal.ZERO, BigDecimal::add);
        int mismatchCount = (int) settlementNow.stream().filter(SettlementAnalyticsRow::isMismatch).count();

        return new OverviewResponse(filter.startDate(), filter.endDate(),
                orderCount, changeRate(orderCount, prevOrderCount),
                orderAmount, changeRate(orderAmount, prevOrderAmount),
                avg, changeRate(avg, prevAvg),
                round1(approvalRate), round1(approvalRate - prevApprovalRate),
                diffNow, changeRate(diffNow, diffPrev),
                mismatchCount);
    }

    /** [requestCount, approvalCount] — 결제 승인율 계산용 원본. */
    private long[] paymentApproval(AnalyticsFilter filter) {
        long request = 0, approval = 0;
        for (PaymentTransaction tx : payments.findAll()) {
            LocalDateTime at = tx.getApprovedAt() != null ? tx.getApprovedAt() : tx.getCreatedAt();
            if (at == null || !filter.matchesDate(at.toLocalDate())) continue;
            if (filter.storeId() != null && !filter.storeId().equals(tx.getStoreId())) continue;
            request++;
            if (APPROVED_STATUSES.contains(tx.getPaymentStatus())) approval++;
        }
        return new long[]{request, approval};
    }

    /* ─────────────────────────── 유틸 ─────────────────────────── */

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String nvl(String v) {
        return v == null ? "" : v;
    }

    private static double rate(long numerator, long denominator) {
        return denominator == 0 ? 0d : numerator * 100d / denominator;
    }

    private static double round1(double v) {
        return Math.round(v * 10d) / 10d;
    }

    private static double changeRate(long now, long previous) {
        return previous == 0 ? 0d : round1((now - previous) * 100d / previous);
    }

    private static double changeRate(BigDecimal now, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) return 0d;
        return round1(now.subtract(previous)
                .divide(previous.abs(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue());
    }
}
