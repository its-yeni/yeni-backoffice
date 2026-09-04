package com.yeni.backoffice.api.dashboard.service;

import com.yeni.backoffice.api.dashboard.dto.OperationsDashboardView;
import com.yeni.backoffice.api.dashboard.dto.OperationsDashboardView.DailySalesPoint;
import com.yeni.backoffice.api.dashboard.dto.OperationsDashboardView.OperationsQueueItem;
import com.yeni.backoffice.api.dashboard.dto.OperationsDashboardView.ProcessingRatio;
import com.yeni.backoffice.api.dashboard.dto.OperationsDashboardView.StorePerformance;
import com.yeni.backoffice.core.commerce.entity.CommerceOrder;
import com.yeni.backoffice.core.commerce.entity.CommerceReturn;
import com.yeni.backoffice.core.commerce.enums.DeliveryStatus;
import com.yeni.backoffice.core.commerce.enums.OrderStatus;
import com.yeni.backoffice.core.commerce.enums.ProductSaleStatus;
import com.yeni.backoffice.core.commerce.enums.RefundStatus;
import com.yeni.backoffice.core.commerce.enums.ReturnStatus;
import com.yeni.backoffice.core.commerce.repository.CommerceDeliveryRepository;
import com.yeni.backoffice.core.commerce.repository.CommerceOrderItemRepository;
import com.yeni.backoffice.core.commerce.repository.CommerceOrderRepository;
import com.yeni.backoffice.core.commerce.repository.CommerceReturnRepository;
import com.yeni.backoffice.core.commerce.repository.ProductVariantRepository;
import com.yeni.backoffice.core.commerce.repository.StoreVariantInventoryRepository;
import com.yeni.backoffice.core.commerce.service.ShipmentService;
import com.yeni.backoffice.core.payment.entity.SettlementStatement;
import com.yeni.backoffice.core.payment.enums.PaymentStatus;
import com.yeni.backoffice.core.payment.enums.RecoveryStatus;
import com.yeni.backoffice.core.payment.enums.SaleType;
import com.yeni.backoffice.core.payment.enums.SettlementStatus;
import com.yeni.backoffice.core.payment.repository.PaymentRecoveryTaskRepository;
import com.yeni.backoffice.core.payment.repository.PaymentTransactionRepository;
import com.yeni.backoffice.core.payment.repository.SalesTransactionRepository;
import com.yeni.backoffice.core.payment.repository.SettlementStatementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OperationsDashboardService {
    // 배송 지연 판단 기준(시간) — deliveries.js의 "지연 건만 보기" 필터와 같은 기준값을 쓴다.
    private static final long DELIVERY_STALE_HOURS = 72;
    private static final List<Integer> SALES_TREND_OPTIONS = List.of(7, 14, 30);

    private final PaymentTransactionRepository payments;
    private final PaymentRecoveryTaskRepository recoveryTasks;
    private final SettlementStatementRepository settlements;
    private final ProductVariantRepository variants;
    private final ShipmentService shipmentService;
    private final CommerceReturnRepository returns;
    private final CommerceDeliveryRepository deliveries;
    private final CommerceOrderRepository orders;
    private final CommerceOrderItemRepository orderItems;
    private final SalesTransactionRepository salesTransactions;
    private final StoreVariantInventoryRepository storeInventories;

    public OperationsDashboardService(PaymentTransactionRepository payments,
            PaymentRecoveryTaskRepository recoveryTasks,
            SettlementStatementRepository settlements,
            ProductVariantRepository variants,
            ShipmentService shipmentService,
            CommerceReturnRepository returns,
            CommerceDeliveryRepository deliveries,
            CommerceOrderRepository orders,
            CommerceOrderItemRepository orderItems,
            SalesTransactionRepository salesTransactions,
            StoreVariantInventoryRepository storeInventories) {
        this.payments = payments;
        this.recoveryTasks = recoveryTasks;
        this.settlements = settlements;
        this.variants = variants;
        this.shipmentService = shipmentService;
        this.returns = returns;
        this.deliveries = deliveries;
        this.orders = orders;
        this.orderItems = orderItems;
        this.salesTransactions = salesTransactions;
        this.storeInventories = storeInventories;
    }

    @Transactional(readOnly = true)
    public OperationsDashboardView getDashboard(Integer requestedTrendDays) {
        return getDashboard(requestedTrendDays, null);
    }

    @Transactional(readOnly = true)
    public OperationsDashboardView getDashboard(Integer requestedTrendDays, Long storeId) {
        int trendDays = requestedTrendDays != null && SALES_TREND_OPTIONS.contains(requestedTrendDays) ? requestedTrendDays : 7;

        var paymentRows = payments.findAll().stream()
                .filter(payment -> storeId == null || storeId.equals(payment.getStoreId()))
                .toList();
        Set<Long> scopedPaymentIds = paymentRows.stream().map(payment -> payment.getId()).collect(Collectors.toSet());
        long unknown = paymentRows.stream()
                .filter(payment -> payment.getPaymentStatus() == PaymentStatus.APPROVE_UNKNOWN
                        || payment.getPaymentStatus() == PaymentStatus.CANCEL_UNKNOWN
                        || payment.getPaymentStatus() == PaymentStatus.UNKNOWN
                        || payment.getPaymentStatus() == PaymentStatus.CANCEL_RECONCILE_REQUIRED
                        || payment.getPaymentStatus() == PaymentStatus.NETWORK_CANCEL_REQUIRED)
                .count();
        long failed = paymentRows.stream()
                .filter(payment -> payment.getPaymentStatus() == PaymentStatus.APPROVE_FAILED
                        || payment.getPaymentStatus() == PaymentStatus.CANCEL_FAILED
                        || payment.getPaymentStatus() == PaymentStatus.NETWORK_CANCEL_FAILED)
                .count();
        long recovery = recoveryTasks.findAll().stream()
                .filter(task -> storeId == null || (task.getPaymentId() != null && scopedPaymentIds.contains(task.getPaymentId())))
                .filter(task -> task.getStatus() == RecoveryStatus.READY
                        || task.getStatus() == RecoveryStatus.PROCESSING
                        || task.getStatus() == RecoveryStatus.FAILED)
                .count();
        List<SettlementStatement> allSettlements = settlements.findAll().stream()
                .filter(statement -> storeId == null || storeId.equals(statement.getStoreId()))
                .toList();
        long drafts = allSettlements.stream().filter(s -> s.getSettlementStatus() == SettlementStatus.DRAFT).count();
        // 품절(가용재고 0)과 재고부족(0 < 가용재고 <= 안전재고)은 시급성이 다르므로 분리해서 센다 —
        // 품절은 그 SKU가 아예 판매 불가능해진 상태라 CRITICAL, 부족은 아직 팔리는 중이라 MEDIUM.
        var allVariants = variants.findAll();
        Map<Long, Integer> safetyStocks = allVariants.stream().collect(Collectors.toMap(v -> v.getId(), v -> v.getSafetyStock()));
        long soldOut = storeId == null ? allVariants.stream()
                .filter(variant -> variant.getSaleStatus() == ProductSaleStatus.ON_SALE)
                .filter(variant -> variant.getAvailableQuantity() <= 0).count()
                : storeInventories.findByStoreId(storeId).stream().filter(i -> i.isSaleEnabled() && i.getAvailableQuantity() <= 0).count();
        long lowStock = storeId == null ? allVariants.stream()
                .filter(variant -> variant.getSaleStatus() == ProductSaleStatus.ON_SALE)
                .filter(variant -> variant.getAvailableQuantity() > 0 && variant.getAvailableQuantity() <= variant.getSafetyStock()).count()
                : storeInventories.findByStoreId(storeId).stream()
                        .filter(i -> i.isSaleEnabled() && i.getAvailableQuantity() > 0)
                        .filter(i -> i.getAvailableQuantity() <= safetyStocks.getOrDefault(i.getVariantId(), 0)).count();
        long pendingShipment = shipmentService.listPending(storeId).size();
        List<CommerceOrder> allOrders = storeId == null ? orders.findAllByOrderByIdDesc() : orders.findByStoreIdOrderByIdDesc(storeId);
        Set<Long> scopedOrderIds = allOrders.stream().map(CommerceOrder::getId).collect(Collectors.toSet());
        List<CommerceReturn> allReturns = returns.findAllByOrderByIdDesc().stream()
                .filter(row -> storeId == null || scopedOrderIds.contains(row.getOrderId())).toList();
        long refundFailed = allReturns.stream().filter(r -> r.getRefundStatus() == RefundStatus.FAILED).count();
        long returnRequested = allReturns.stream().filter(r -> r.getStatus() == ReturnStatus.REQUESTED).count();
        LocalDateTime staleBefore = LocalDateTime.now().minusHours(DELIVERY_STALE_HOURS);
        long deliveryDelayed = deliveries.findByStatusOrderByIdDesc(DeliveryStatus.IN_TRANSIT).stream()
                .filter(delivery -> storeId == null || scopedOrderIds.contains(delivery.getOrderId()))
                .filter(d -> d.getShippedAt() != null && d.getShippedAt().isBefore(staleBefore))
                .count();

        List<OperationsQueueItem> queue = new ArrayList<>();
        queue.add(new OperationsQueueItem("CRITICAL", "결제", "결과 불명 거래 확인",
                "PG 응답만으로 승인·실패를 확정할 수 없어 거래 조회가 필요합니다.", unknown,
                "거래 확인", "/admin/payment-operations?status=UNKNOWN"));
        queue.add(new OperationsQueueItem("CRITICAL", "반품", "환불 실패 건 확인",
                "반품 처리(재고 복원)는 끝났지만 PG 취소가 실패해 실제로 환불이 나가지 않은 상태입니다.", refundFailed,
                "환불 재시도", "/admin/commerce/returns?refundStatus=FAILED"));
        queue.add(new OperationsQueueItem("CRITICAL", "재고", "품절 SKU",
                "가용재고가 0이라 판매 자체가 불가능한 SKU입니다. 입고 또는 판매중지 처리가 필요합니다.", soldOut,
                "재고 확인", "/admin/commerce/inventory?health=SOLD_OUT"));
        queue.add(new OperationsQueueItem("HIGH", "복구", "복구 작업 처리",
                "결제 결과 조회, 망취소 또는 후속 전송 재시도가 필요한 작업입니다.", recovery,
                "복구 작업", "/admin/payment-operations/recovery-tasks?status=READY"));
        queue.add(new OperationsQueueItem("HIGH", "결제", "승인 실패 원인 확인",
                "고객 재시도 전에 실패 사유와 결제 수단 오류를 확인합니다.", failed,
                "실패 거래", "/admin/payment-operations?status=APPROVE_FAILED"));
        queue.add(new OperationsQueueItem("HIGH", "반품", "반품 검수 대기",
                "고객이 접수했지만 아직 검수를 시작하지 않은 반품입니다.", returnRequested,
                "검수 시작", "/admin/commerce/returns?status=REQUESTED"));
        queue.add(new OperationsQueueItem("MEDIUM", "정산", "정산 초안 검토",
                "확정 전 승인·취소 원장과 수수료 계산 근거를 대사해야 합니다.", drafts,
                "초안 검토", "/admin/payment-operations/settlements?status=DRAFT"));
        queue.add(new OperationsQueueItem("MEDIUM", "재고", "안전재고 이하 SKU",
                "가용재고가 안전재고 이하(품절 제외)인 판매 SKU의 입고 여부를 판단합니다.", lowStock,
                "재고 확인", "/admin/commerce/inventory?health=LOW"));
        queue.add(new OperationsQueueItem("MEDIUM", "물류", "출고 대기 상품",
                "결제 완료됐지만 아직 창고에서 출고 처리되지 않은 주문 상품입니다.", pendingShipment,
                "출고 처리", "/admin/commerce/shipments"));
        queue.add(new OperationsQueueItem("MEDIUM", "배송", "배송 지연 건",
                String.format("배송 중 상태로 %d시간 넘게 머물러 있는 건입니다.", DELIVERY_STALE_HOURS), deliveryDelayed,
                "배송 확인", "/admin/commerce/deliveries?status=IN_TRANSIT&stale=1"));

        long actionRequired = unknown + failed + recovery + drafts + lowStock + soldOut + pendingShipment
                + refundFailed + returnRequested + deliveryDelayed;

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);

        // "오늘 현황" 카드: 실제 일별 흐름 지표라 어제와 비교한 증감률을 낼 수 있다.
        long todayOrderCount = allOrders.stream().filter(o -> today.equals(o.getCreatedAt().toLocalDate())).count();
        long yesterdayOrderCount = allOrders.stream().filter(o -> yesterday.equals(o.getCreatedAt().toLocalDate())).count();
        long todayPaidOrderCount = allOrders.stream()
                .filter(o -> today.equals(o.getCreatedAt().toLocalDate()) && o.getOrderStatus() == OrderStatus.PAID)
                .count();
        long todayReturnRequested = allReturns.stream()
                .filter(r -> today.equals(r.getCreatedAt().toLocalDate())).count();

        List<Object[]> salesRows = salesTransactions.findByBusinessDateBetweenOrderByOccurredAtDesc(
                        today.minusDays(trendDays - 1L), today).stream()
                .filter(s -> storeId == null || storeId.equals(s.getStoreId()))
                .map(s -> new Object[]{s.getBusinessDate(), s.getSaleType(), s.getTotalAmount(), s.getOrderNo()})
                .toList();
        Map<LocalDate, List<Object[]>> byDate = salesRows.stream().collect(Collectors.groupingBy(r -> (LocalDate) r[0]));
        List<DailySalesPoint> salesTrend = new ArrayList<>();
        for (int i = trendDays - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            List<Object[]> rows = byDate.getOrDefault(day, List.of());
            // SalesTransaction.totalAmount is already signed: SALE is positive and
            // CANCEL is negative. Sum it exactly as the sales-ledger summary does.
            BigDecimal revenue = rows.stream()
                    .map(r -> (BigDecimal) r[2])
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            long orderCount = rows.stream().filter(r -> r[1] == SaleType.SALE).map(r -> (String) r[3]).distinct().count();
            salesTrend.add(new DailySalesPoint(day, revenue, orderCount));
        }
        BigDecimal todayRevenue = salesTrend.isEmpty() ? BigDecimal.ZERO : salesTrend.get(salesTrend.size() - 1).revenue();
        BigDecimal yesterdayRevenue = salesTrend.size() < 2 ? BigDecimal.ZERO : salesTrend.get(salesTrend.size() - 2).revenue();

        Map<Long, List<CommerceOrder>> ordersByStore = allOrders.stream()
                .filter(order -> order.getStoreId() != null)
                .collect(Collectors.groupingBy(CommerceOrder::getStoreId));
        Map<Long, BigDecimal> confirmedRevenueByStore = salesTransactions
                .findByBusinessDateBetweenOrderByOccurredAtDesc(today.minusDays(trendDays - 1L), today).stream()
                .filter(row -> Boolean.TRUE.equals(row.getConfirmedYn()))
                .filter(row -> row.getStoreId() != null)
                .filter(row -> storeId == null || storeId.equals(row.getStoreId()))
                .collect(Collectors.groupingBy(row -> row.getStoreId(), Collectors.reducing(
                        BigDecimal.ZERO,
                        row -> row.getTotalAmount(),
                        BigDecimal::add)));
        List<StorePerformance> storePerformance = ordersByStore.entrySet().stream()
                .map(entry -> new StorePerformance(entry.getKey(),
                        entry.getValue().stream().map(CommerceOrder::getStoreName).filter(java.util.Objects::nonNull).findFirst().orElse("미지정 매장"),
                        entry.getValue().size(), confirmedRevenueByStore.getOrDefault(entry.getKey(), BigDecimal.ZERO)))
                .sorted(java.util.Comparator.comparing(StorePerformance::confirmedRevenue).reversed())
                .limit(5)
                .toList();

        // "오늘 처리 현황" 진행률: (오늘 처리 완료 / (오늘 처리 완료 + 지금 남은 잔여)). 과거 스냅샷 없이도
        // "오늘 새로 생긴 일감을 오늘 얼마나 소화했는지"를 정직하게 보여줄 수 있는 유일한 실제 계산 방식이다.
        ProcessingRatio orderProcessing = new ProcessingRatio(todayPaidOrderCount, todayOrderCount);
        long shippedToday = storeId == null
                ? orderItems.countByShippedYnTrueAndShippedAtBetween(todayStart, tomorrowStart)
                : scopedOrderIds.isEmpty() ? 0
                : orderItems.countByOrderIdInAndShippedYnTrueAndShippedAtBetween(scopedOrderIds, todayStart, tomorrowStart);
        ProcessingRatio shipmentProcessing = new ProcessingRatio(shippedToday, shippedToday + pendingShipment);
        long returnsProcessedToday = allReturns.stream()
                .filter(r -> r.getStatus() != ReturnStatus.REQUESTED)
                .filter(r -> (r.getInspectedAt() != null && today.equals(r.getInspectedAt().toLocalDate())))
                .count();
        ProcessingRatio returnProcessing = new ProcessingRatio(returnsProcessedToday, returnsProcessedToday + returnRequested);
        long settlementsConfirmedToday = allSettlements.stream()
                .filter(s -> s.getSettlementStatus() != SettlementStatus.DRAFT)
                .filter(s -> s.getUpdatedAt() != null && today.equals(s.getUpdatedAt().toLocalDate()))
                .count();
        ProcessingRatio settlementProcessing = new ProcessingRatio(settlementsConfirmedToday, settlementsConfirmedToday + drafts);

        return new OperationsDashboardView(LocalDateTime.now(), trendDays,
                actionRequired, unknown, failed, recovery, drafts, lowStock,
                soldOut, pendingShipment, refundFailed, returnRequested, deliveryDelayed,
                todayOrderCount, changePercent(todayOrderCount, yesterdayOrderCount),
                todayRevenue, changePercent(todayRevenue, yesterdayRevenue), todayReturnRequested,
                unknown + failed + recovery, deliveryDelayed + pendingShipment, soldOut + lowStock,
                returnRequested + refundFailed + drafts,
                orderProcessing, shipmentProcessing, returnProcessing, settlementProcessing,
                salesTrend, storePerformance, queue);
    }

    private Double changePercent(long todayValue, long yesterdayValue) {
        if (yesterdayValue == 0) return null;
        return Math.round(((todayValue - yesterdayValue) * 1000.0 / yesterdayValue)) / 10.0;
    }

    private Double changePercent(BigDecimal todayValue, BigDecimal yesterdayValue) {
        if (yesterdayValue.compareTo(BigDecimal.ZERO) == 0) return null;
        double change = (todayValue.doubleValue() - yesterdayValue.doubleValue()) / yesterdayValue.doubleValue() * 100.0;
        return Math.round(change * 10.0) / 10.0;
    }
}
