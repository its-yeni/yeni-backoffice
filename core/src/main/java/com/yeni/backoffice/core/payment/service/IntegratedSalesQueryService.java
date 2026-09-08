package com.yeni.backoffice.core.payment.service;

import com.yeni.backoffice.core.commerce.entity.CommerceDelivery;
import com.yeni.backoffice.core.commerce.entity.CommerceOrder;
import com.yeni.backoffice.core.commerce.enums.DeliveryStatus;
import com.yeni.backoffice.core.commerce.repository.CommerceDeliveryRepository;
import com.yeni.backoffice.core.commerce.repository.CommerceOrderRepository;
import com.yeni.backoffice.core.payment.dto.IntegratedSalesDtos.IntegratedSaleRow;
import com.yeni.backoffice.core.payment.dto.IntegratedSalesDtos.IntegratedSalesResponse;
import com.yeni.backoffice.core.payment.dto.IntegratedSalesDtos.StageCount;
import com.yeni.backoffice.core.payment.entity.PaymentTransaction;
import com.yeni.backoffice.core.payment.entity.PgSettlementReconciliation;
import com.yeni.backoffice.core.payment.entity.SalesTransaction;
import com.yeni.backoffice.core.payment.entity.SettlementStatement;
import com.yeni.backoffice.core.payment.enums.PgReconciliationResolutionStatus;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 통합 매출 조회 — 주문 1건의 전체 흐름(주문 → 결제 → 배송 → 매출원장 → 구매확정 → 대사 → 정산 → 지급)을
 * 한 행에 모아 보여준다. 데모 규모라 in-memory 조인.
 */
@Service
public class IntegratedSalesQueryService {

    private final CommerceOrderRepository orders;
    private final PaymentTransactionRepository payments;
    private final CommerceDeliveryRepository deliveries;
    private final SalesTransactionRepository sales;
    private final SettlementDetailRepository settlementDetails;
    private final SettlementStatementRepository settlements;
    private final PgSettlementReconciliationRepository reconciliations;
    private final PgSettlementImportRepository imports;

    public IntegratedSalesQueryService(CommerceOrderRepository orders, PaymentTransactionRepository payments,
                                       CommerceDeliveryRepository deliveries, SalesTransactionRepository sales,
                                       SettlementDetailRepository settlementDetails, SettlementStatementRepository settlements,
                                       PgSettlementReconciliationRepository reconciliations, PgSettlementImportRepository imports) {
        this.orders = orders;
        this.payments = payments;
        this.deliveries = deliveries;
        this.sales = sales;
        this.settlementDetails = settlementDetails;
        this.settlements = settlements;
        this.reconciliations = reconciliations;
        this.imports = imports;
    }

    private static final List<String[]> STAGES = List.of(
            new String[]{"PAYMENT", "결제 확인 필요"},
            new String[]{"FULFILL", "출고·배송"},
            new String[]{"CONFIRM", "구매 확정 대기"},
            new String[]{"RECON", "PG 대사"},
            new String[]{"SETTLE", "정산 대기"},
            new String[]{"PAYOUT", "지급 대기"},
            new String[]{"DONE", "완료"}
    );

    @Transactional(readOnly = true)
    public IntegratedSalesResponse query(LocalDate startDate, LocalDate endDate, Long storeId, String keyword, String stage) {
        LocalDateTime from = (startDate == null ? LocalDate.now().minusDays(7) : startDate).atStartOfDay();
        LocalDateTime to = (endDate == null ? LocalDate.now() : endDate).plusDays(1).atStartOfDay();
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();

        List<CommerceOrder> orderList = orders.findAllByOrderByIdDesc().stream()
                .filter(o -> o.getCreatedAt() != null && !o.getCreatedAt().isBefore(from) && o.getCreatedAt().isBefore(to))
                .filter(o -> storeId == null || storeId.equals(o.getStoreId()))
                .filter(o -> kw.isEmpty()
                        || o.getOrderNo().toLowerCase().contains(kw)
                        || (o.getBuyerName() != null && o.getBuyerName().toLowerCase().contains(kw))
                        || (o.getProductName() != null && o.getProductName().toLowerCase().contains(kw)))
                .collect(Collectors.toList());

        Map<String, PaymentTransaction> paymentByOrder = payments.findAll().stream()
                .collect(Collectors.toMap(PaymentTransaction::getOrderNo, Function.identity(), (a, b) -> a));
        Map<Long, List<CommerceDelivery>> deliveryByOrderId = deliveries.findByOrderIdIn(
                        orderList.stream().map(CommerceOrder::getId).toList()).stream()
                .collect(Collectors.groupingBy(CommerceDelivery::getOrderId));
        Map<String, List<SalesTransaction>> salesByOrder = sales.findAll().stream()
                .collect(Collectors.groupingBy(SalesTransaction::getOrderNo));
        Map<Long, SettlementStatement> statementById = settlements.findAll().stream()
                .collect(Collectors.toMap(SettlementStatement::getId, Function.identity()));
        Map<Long, Long> statementBySalesId = settlementDetails.findAll().stream()
                .collect(Collectors.toMap(d -> d.getSalesId(), d -> d.getSettlementStatementId(), (a, b) -> a));
        Map<String, PgReconciliationResolutionStatus> reconByOrder = new LinkedHashMap<>();
        Map<String, Boolean> reconMatchedByOrder = new LinkedHashMap<>();
        for (var imp : imports.findAll()) {
            for (PgSettlementReconciliation row : reconciliations.findByImportIdOrderById(imp.getId())) {
                if (row.getOrderNo() == null) continue;
                reconByOrder.merge(row.getOrderNo(), row.getResolutionStatus(),
                        (a, b) -> b == PgReconciliationResolutionStatus.OPEN || b == PgReconciliationResolutionStatus.IN_REVIEW ? b : a);
                boolean matched = row.getResolutionStatus() == PgReconciliationResolutionStatus.RESOLVED
                        || row.getResolutionStatus() == PgReconciliationResolutionStatus.EXCLUDED
                        || row.getResolutionStatus() == PgReconciliationResolutionStatus.NOT_REQUIRED;
                reconMatchedByOrder.merge(row.getOrderNo(), matched, (a, b) -> a && b);
            }
        }

        List<IntegratedSaleRow> rows = new ArrayList<>();
        for (CommerceOrder o : orderList) {
            PaymentTransaction pay = paymentByOrder.get(o.getOrderNo());
            List<CommerceDelivery> dv = deliveryByOrderId.getOrDefault(o.getId(), List.of());
            List<SalesTransaction> sx = salesByOrder.getOrDefault(o.getOrderNo(), List.of());
            rows.add(toRow(o, pay, dv, sx, statementBySalesId, statementById, reconByOrder.get(o.getOrderNo()),
                    reconMatchedByOrder.get(o.getOrderNo())));
        }

        List<IntegratedSaleRow> filtered = stage == null || stage.isBlank()
                ? rows : rows.stream().filter(r -> stage.equals(r.stage())).collect(Collectors.toList());
        filtered.sort(Comparator.comparing(IntegratedSaleRow::orderedAt).reversed());

        BigDecimal total = filtered.stream().map(IntegratedSaleRow::amount).filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cancelled = filtered.stream().map(IntegratedSaleRow::cancelledAmount).filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Long> byStage = rows.stream().collect(Collectors.groupingBy(IntegratedSaleRow::stage, Collectors.counting()));
        List<StageCount> stageCounts = STAGES.stream()
                .map(s -> new StageCount(s[0], s[1], byStage.getOrDefault(s[0], 0L)))
                .collect(Collectors.toList());

        return new IntegratedSalesResponse(filtered.size(), total, total.subtract(cancelled), stageCounts, filtered);
    }

    private IntegratedSaleRow toRow(CommerceOrder o, PaymentTransaction pay, List<CommerceDelivery> dv,
                                    List<SalesTransaction> sx, Map<Long, Long> statementBySalesId,
                                    Map<Long, SettlementStatement> statementById,
                                    PgReconciliationResolutionStatus recon, Boolean reconMatched) {
        String paymentStatus = pay == null ? o.getPaymentStatus().name() : pay.getPaymentStatus().name();
        String cardBrief = pay == null || !"CARD".equalsIgnoreCase(pay.getPaymentMethod()) ? "현금"
                : ((pay.getIssuerName() == null ? "카드" : pay.getIssuerName())
                   + (pay.getCardLast4() == null ? "" : " ••" + pay.getCardLast4())
                   + (pay.getInstallmentMonths() != null && pay.getInstallmentMonths() > 0 ? " " + pay.getInstallmentMonths() + "개월" : ""));
        String deliveryStatus = deliverySummary(dv);

        SalesTransaction saleHeader = sx.stream().filter(s -> s.getSaleType() == SaleType.SALE).findFirst().orElse(null);
        boolean hasCancel = sx.stream().anyMatch(s -> s.getSaleType() == SaleType.CANCEL);
        String ledgerState = saleHeader == null ? "NONE" : hasCancel ? "PARTIAL_CANCEL" : "SALE";
        boolean confirmed = saleHeader != null && Boolean.TRUE.equals(saleHeader.getConfirmedYn());

        Long statementId = saleHeader == null ? null : statementBySalesId.get(saleHeader.getId());
        SettlementStatement st = statementId == null ? null : statementById.get(statementId);
        String settlementState;
        if (st == null) settlementState = "NONE";
        else if (!"NONE".equals(st.getApprovalStage()) && st.getApprovalStage() != null) settlementState = st.getApprovalStage();
        else settlementState = st.getSettlementStatus().name();

        String reconState = recon == null ? "NONE"
                : (Boolean.TRUE.equals(reconMatched) ? "MATCH" : "MISMATCH");

        BigDecimal amount = o.getPayableAmount();
        BigDecimal cancelledAmount = o.getCancelledAmount() == null ? BigDecimal.ZERO : o.getCancelledAmount();

        String[] next = nextStep(o, paymentStatus, deliveryStatus, confirmed, reconState, settlementState, statementId, pay);

        return new IntegratedSaleRow(
                o.getId(), o.getOrderNo(), o.getCreatedAt(),
                o.getStoreName() == null ? "-" : o.getStoreName(),
                o.getChannelType() == null ? "WEB" : o.getChannelType(),
                o.getProductName() == null ? "-" : o.getProductName(),
                sx.isEmpty() ? 1 : sx.size(),
                amount, cancelledAmount,
                paymentStatus, pay == null ? null : pay.getId(), cardBrief,
                pay == null ? null : pay.getAcquiringStatus(),
                deliveryStatus, ledgerState, confirmed, reconState, settlementState, statementId,
                next[0], next[1], next[2]
        );
    }

    private String deliverySummary(List<CommerceDelivery> dv) {
        if (dv.isEmpty()) return "배송 정보 없음";
        boolean allDone = dv.stream().allMatch(d -> d.getStatus() == DeliveryStatus.DELIVERED || d.getStatus() == DeliveryStatus.RETURNED);
        if (allDone && dv.stream().anyMatch(d -> d.getStatus() == DeliveryStatus.DELIVERED)) return "배송 완료";
        if (dv.stream().anyMatch(d -> d.getStatus() == DeliveryStatus.IN_TRANSIT)) return "배송 중";
        if (dv.stream().anyMatch(d -> d.getStatus() == DeliveryStatus.RETURNED)) return "일부 반송";
        return "배송 준비";
    }

    /** [stage, nextLabel, nextHref] */
    private String[] nextStep(CommerceOrder o, String paymentStatus, String deliveryStatus, boolean confirmed,
                              String reconState, String settlementState, Long statementId, PaymentTransaction pay) {
        String orderNo = o.getOrderNo();
        String q = java.net.URLEncoder.encode(orderNo, java.nio.charset.StandardCharsets.UTF_8);
        if (paymentStatus.contains("UNKNOWN"))
            return new String[]{"PAYMENT", "결과 재조회", "/admin/payment-operations?paymentId=" + (pay == null ? "" : pay.getId())};
        if (paymentStatus.contains("FAILED"))
            return new String[]{"PAYMENT", "실패 거래 확인", "/admin/payment-operations?keyword=" + q};
        if (settlementState.equals("PAID"))
            return new String[]{"DONE", "완료", ""};
        if (settlementState.equals("PAYOUT_REQUESTED"))
            return new String[]{"PAYOUT", "지급 승인", "/admin/payment-operations/settlements?statementId=" + statementId};
        if (settlementState.equals("CONFIRMED"))
            return new String[]{"PAYOUT", "지급 요청", "/admin/payment-operations/settlements?statementId=" + statementId};
        if (settlementState.equals("CONFIRM_REQUESTED"))
            return new String[]{"SETTLE", "확정 승인", "/admin/payment-operations/settlements?statementId=" + statementId};
        if (settlementState.equals("DRAFT"))
            return new String[]{"SETTLE", "확정 요청", "/admin/payment-operations/settlements?statementId=" + statementId};
        if (confirmed && reconState.equals("MISMATCH"))
            return new String[]{"RECON", "PG 대사", "/admin/payment-operations/settlements/reconciliation"};
        if (confirmed)
            return new String[]{"SETTLE", "정산 생성", "/admin/payment-operations/settlements"};
        if (deliveryStatus.equals("배송 완료"))
            return new String[]{"CONFIRM", "구매 확정", "/admin/payment-operations/pending-sales?keyword=" + q};
        if (deliveryStatus.equals("배송 중"))
            return new String[]{"FULFILL", "배송 완료 처리", "/admin/commerce/deliveries?orderNo=" + q};
        return new String[]{"FULFILL", "출고 처리", "/admin/commerce/shipments?keyword=" + q};
    }
}
