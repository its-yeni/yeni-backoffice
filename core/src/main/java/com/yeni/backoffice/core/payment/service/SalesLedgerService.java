package com.yeni.backoffice.core.payment.service;

import com.yeni.backoffice.core.commerce.entity.CommerceOrder;
import com.yeni.backoffice.core.commerce.entity.CommerceOrderItem;
import com.yeni.backoffice.core.commerce.entity.Product;
import com.yeni.backoffice.core.commerce.repository.CommerceOrderItemRepository;
import com.yeni.backoffice.core.commerce.repository.CommerceOrderRepository;
import com.yeni.backoffice.core.commerce.repository.ProductRepository;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.NotFoundException;
import com.yeni.backoffice.core.common.exception.ValidationBusinessException;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.AlimtalkQueueResponse;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.ExternalSendResponse;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.PaymentResponse;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.RecoveryTaskResponse;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SalesAdjustmentRequest;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SalesLedgerLinksResponse;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SalesLedgerPageResponse;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SalesLedgerSummaryResponse;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SalesResponse;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SettlementDetailResponse;
import com.yeni.backoffice.core.payment.entity.AuditLog;
import com.yeni.backoffice.core.payment.entity.PaymentCancel;
import com.yeni.backoffice.core.payment.entity.PaymentTransaction;
import com.yeni.backoffice.core.payment.entity.SalesTransaction;
import com.yeni.backoffice.core.payment.entity.SalesTransactionLine;
import com.yeni.backoffice.core.payment.entity.SettlementAdjustment;
import com.yeni.backoffice.core.payment.enums.LedgerStatus;
import com.yeni.backoffice.core.payment.enums.SaleStatus;
import com.yeni.backoffice.core.payment.enums.SaleType;
import com.yeni.backoffice.core.payment.enums.SalesSettlementStatus;
import com.yeni.backoffice.core.payment.repository.AlimtalkQueueRepository;
import com.yeni.backoffice.core.payment.repository.AuditLogRepository;
import com.yeni.backoffice.core.payment.repository.ExternalSendRequestRepository;
import com.yeni.backoffice.core.payment.repository.PaymentCancelRepository;
import com.yeni.backoffice.core.payment.repository.PaymentRecoveryTaskRepository;
import com.yeni.backoffice.core.payment.repository.PaymentTransactionRepository;
import com.yeni.backoffice.core.payment.repository.SalesTransactionLineRepository;
import com.yeni.backoffice.core.payment.repository.SalesTransactionRepository;
import com.yeni.backoffice.core.payment.repository.SettlementAdjustmentRepository;
import com.yeni.backoffice.core.payment.repository.SettlementDetailRepository;
import com.yeni.backoffice.core.payment.support.PaymentDefaults;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SalesLedgerService {

    private final SalesTransactionRepository salesRepository;
    private final PaymentTransactionRepository paymentRepository;
    private final PaymentCancelRepository cancelRepository;
    private final ExternalSendRequestRepository externalSendRequestRepository;
    private final AlimtalkQueueRepository alimtalkQueueRepository;
    private final PaymentRecoveryTaskRepository recoveryTaskRepository;
    private final SettlementAdjustmentRepository adjustmentRepository;
    private final SettlementDetailRepository settlementDetailRepository;
    private final AuditLogRepository auditLogRepository;
    private final CommerceOrderRepository commerceOrderRepository;
    private final CommerceOrderItemRepository commerceOrderItemRepository;
    private final ProductRepository productRepository;
    private final SalesTransactionLineRepository salesLineRepository;
    private final PendingSalesQueryService pendingSalesQueryService;

    public SalesLedgerService(
            SalesTransactionRepository salesRepository,
            PaymentTransactionRepository paymentRepository,
            PaymentCancelRepository cancelRepository,
            ExternalSendRequestRepository externalSendRequestRepository,
            AlimtalkQueueRepository alimtalkQueueRepository,
            PaymentRecoveryTaskRepository recoveryTaskRepository,
            SettlementAdjustmentRepository adjustmentRepository,
            SettlementDetailRepository settlementDetailRepository,
            AuditLogRepository auditLogRepository,
            CommerceOrderRepository commerceOrderRepository,
            CommerceOrderItemRepository commerceOrderItemRepository,
            ProductRepository productRepository,
            SalesTransactionLineRepository salesLineRepository,
            PendingSalesQueryService pendingSalesQueryService) {
        this.salesRepository = salesRepository;
        this.pendingSalesQueryService = pendingSalesQueryService;
        this.paymentRepository = paymentRepository;
        this.cancelRepository = cancelRepository;
        this.externalSendRequestRepository = externalSendRequestRepository;
        this.alimtalkQueueRepository = alimtalkQueueRepository;
        this.recoveryTaskRepository = recoveryTaskRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.settlementDetailRepository = settlementDetailRepository;
        this.auditLogRepository = auditLogRepository;
        this.commerceOrderRepository = commerceOrderRepository;
        this.commerceOrderItemRepository = commerceOrderItemRepository;
        this.productRepository = productRepository;
        this.salesLineRepository = salesLineRepository;
    }

    @Transactional
    public SalesTransaction createSales(PaymentTransaction payment, SaleType saleType, Long sourceId, BigDecimal amount, LocalDateTime occurredAt) {
        String sourceType = saleType.name();
        return salesRepository.findBySourceTypeAndSourceId(sourceType, sourceId)
                .orElseGet(() -> saveNewSales(payment, saleType, sourceId, amount, occurredAt));
    }

    @Transactional(readOnly = true)
    public SalesLedgerPageResponse getSalesLedger(
            LocalDate startDate,
            LocalDate endDate,
            String transactionType,
            String ledgerStatus,
            String settlementStatus,
            String keyword,
            int page,
            int size) {
        return getSalesLedger(startDate, endDate, transactionType, ledgerStatus, settlementStatus, keyword, page, size, null);
    }

    /**
     * 미확정 매출 목록 — 배송 완료(구매 확정) 전이라 아직 정산 대상이 아닌 SALE 매출.
     * 각 행에 매출 명세 라인에서 뽑은 분류명·상품 요약을 붙인다.
     */
    @Transactional(readOnly = true)
    public com.yeni.backoffice.core.payment.dto.SalesAnalyticsDtos.PendingSalesResponse getPendingSales(
            LocalDate startDate, LocalDate endDate, Long storeId, String keyword) {
        return pendingSalesQueryService.getPendingSales(startDate, endDate, storeId, keyword);
    }

    /** Converts approved ledger rows into settlement candidates after purchase confirmation. */
    @Transactional
    public int confirmOrderSales(String orderNo, LocalDateTime confirmedAt) {
        List<SalesTransaction> rows = salesRepository.findByOrderNoOrderByIdAsc(orderNo);
        rows.forEach(row -> row.confirm(confirmedAt));
        if (!rows.isEmpty()) {
            salesLineRepository.findBySalesTransactionIdInOrderByIdAsc(
                    rows.stream().map(SalesTransaction::getId).toList()).forEach(SalesTransactionLine::confirm);
        }
        return rows.size();
    }

    @Transactional(readOnly = true)
    public SalesLedgerPageResponse getSalesLedger(
            LocalDate startDate, LocalDate endDate, String transactionType,
            String ledgerStatus, String settlementStatus, String keyword,
            int page, int size, Long storeId) {
        return getSalesLedger(startDate, endDate, transactionType, ledgerStatus, settlementStatus,
                keyword, page, size, storeId, null);
    }

    @Transactional(readOnly = true)
    public SalesLedgerPageResponse getSalesLedger(
            LocalDate startDate, LocalDate endDate, String transactionType,
            String ledgerStatus, String settlementStatus, String keyword,
            int page, int size, Long storeId, Boolean confirmedYn) {
        LocalDate start = startDate == null ? LocalDate.now().minusDays(30) : startDate;
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        int normalizedSize = size <= 0 ? 15 : Math.min(size, 100);
        int normalizedPage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(normalizedPage, normalizedSize, Sort.by(Sort.Direction.DESC, "occurredAt", "id"));
        Page<SalesTransaction> result = salesRepository.searchLedger(
                start,
                end,
                storeId,
                parseSaleType(transactionType),
                parseLedgerStatus(ledgerStatus),
                parseSettlementStatus(settlementStatus),
                confirmedYn,
                normalizeKeyword(keyword),
                pageable
        );
        return new SalesLedgerPageResponse(
                result.getContent().stream().map(SalesResponse::from).toList(),
                result.getTotalElements(),
                normalizedPage,
                normalizedSize,
                summarizeSalesLedger(start, end, transactionType, ledgerStatus, settlementStatus, keyword, storeId, confirmedYn)
        );
    }

    @Transactional(readOnly = true)
    public SalesLedgerSummaryResponse getSalesLedgerSummary(
            LocalDate startDate,
            LocalDate endDate,
            String transactionType,
            String ledgerStatus,
            String settlementStatus,
            String keyword) {
        return getSalesLedgerSummary(startDate, endDate, transactionType, ledgerStatus, settlementStatus, keyword, null);
    }

    @Transactional(readOnly = true)
    public SalesLedgerSummaryResponse getSalesLedgerSummary(
            LocalDate startDate, LocalDate endDate, String transactionType,
            String ledgerStatus, String settlementStatus, String keyword, Long storeId) {
        LocalDate start = startDate == null ? LocalDate.now().minusDays(30) : startDate;
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        return summarizeSalesLedger(start, end, transactionType, ledgerStatus, settlementStatus, keyword, storeId);
    }

    @Transactional(readOnly = true)
    public List<SalesResponse> getSales(LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate == null ? LocalDate.now().minusDays(30) : startDate;
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        return salesRepository.findByBusinessDateBetweenOrderByOccurredAtDesc(start, end).stream()
                .map(SalesResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SalesResponse getSalesLedgerDetail(Long salesTransactionId) {
        return salesRepository.findById(salesTransactionId)
                .map(SalesResponse::from)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SALES_TRANSACTION_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public SalesLedgerLinksResponse getSalesLedgerLinks(Long salesTransactionId) {
        SalesTransaction sales = salesRepository.findById(salesTransactionId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SALES_TRANSACTION_NOT_FOUND));
        SalesResponse originalSale = sales.getOriginalSalesTransactionId() == null ? null
                : salesRepository.findById(sales.getOriginalSalesTransactionId()).map(SalesResponse::from).orElse(null);
        PaymentTransaction payment = sales.getPaymentId() == null ? null
                : paymentRepository.findById(sales.getPaymentId()).orElse(null);
        PaymentCancel cancel = sales.getCancelId() == null ? null
                : cancelRepository.findById(sales.getCancelId()).orElse(null);
        List<RecoveryTaskResponse> recoveryTasks = sales.getPaymentId() == null
                ? List.of()
                : recoveryTaskRepository.findByPaymentIdOrderByIdAsc(sales.getPaymentId()).stream()
                .map(RecoveryTaskResponse::from)
                .toList();

        return new SalesLedgerLinksResponse(
                sales.getId(),
                originalSale,
                payment == null ? null : PaymentResponse.from(payment),
                cancel == null ? null : cancel.getId(),
                payment == null ? BigDecimal.ZERO : payment.getCanceledAmount(),
                payment == null ? BigDecimal.ZERO : payment.getCancelableAmount(),
                cancel == null ? null : cancel.getCancelReason(),
                cancel == null ? null : cancel.getCanceledAt(),
                externalSendRequestRepository.findBySalesIdOrderByIdAsc(sales.getId()).stream()
                        .map(ExternalSendResponse::from)
                        .toList(),
                alimtalkQueueRepository.findBySalesIdOrderByIdAsc(sales.getId()).stream()
                        .map(AlimtalkQueueResponse::from)
                        .toList(),
                recoveryTasks,
                settlementDetailRepository.findBySalesIdOrderByIdAsc(sales.getId()).stream()
                        .map(SettlementDetailResponse::from)
                        .toList()
        );
    }

    @Transactional
    public void addSalesAdjustment(Long salesId, SalesAdjustmentRequest request) {
        if (!StringUtils.hasText(request.adjustmentType()) || request.adjustmentAmount() == null) {
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "조정 유형과 조정금액은 필수입니다.");
        }
        salesRepository.findById(salesId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SALES_TRANSACTION_NOT_FOUND));
        adjustmentRepository.save(SettlementAdjustment.builder()
                .salesId(salesId)
                .adjustmentType(request.adjustmentType())
                .adjustmentAmount(request.adjustmentAmount())
                .reason(StringUtils.hasText(request.reason()) ? request.reason() : "Portfolio mock adjustment")
                .build());
        saveAudit("SALES", "ADJUSTMENT", String.valueOf(salesId), "매출 정산 조정 데이터를 등록했습니다.");
    }

    private SalesTransaction saveNewSales(PaymentTransaction payment, SaleType saleType, Long sourceId, BigDecimal amount, LocalDateTime occurredAt) {
        BigDecimal totalAmount = amount.setScale(0, RoundingMode.HALF_UP);
        BigDecimal supplyAmount = calculateSupplyAmount(totalAmount);
        BigDecimal vatAmount = totalAmount.subtract(supplyAmount);
        Long cancelId = SaleType.CANCEL.equals(saleType) ? sourceId : null;
        Long originalSalesTransactionId = SaleType.CANCEL.equals(saleType) ? findOriginalSaleId(payment.getOrderNo()) : null;
        boolean confirmed = SaleType.CANCEL.equals(saleType)
                ? salesRepository.findFirstByOrderNoAndSaleTypeOrderByIdAsc(payment.getOrderNo(), SaleType.SALE)
                        .map(row -> Boolean.TRUE.equals(row.getConfirmedYn())).orElse(true)
                : commerceOrderRepository.findByOrderNo(payment.getOrderNo()).isEmpty();
        SalesTransaction sales = SalesTransaction.builder()
                .storeId(payment.getStoreId())
                .sourceType(saleType.name())
                .sourceId(sourceId)
                .paymentId(payment.getId())
                .cancelId(cancelId)
                .originalSalesTransactionId(originalSalesTransactionId)
                .orderNo(payment.getOrderNo())
                .tid(payment.getTid())
                .pgTransactionId(payment.getTid())
                .saleType(saleType)
                .saleAmount(totalAmount)
                .supplyAmount(supplyAmount)
                .vatAmount(vatAmount)
                .totalAmount(totalAmount)
                .saleStatus(SaleStatus.READY)
                .ledgerStatus(LedgerStatus.POSTED)
                .settlementStatus(SalesSettlementStatus.NOT_SETTLED)
                .businessDate(occurredAt.toLocalDate())
                .occurredAt(occurredAt)
                .pgCode(payment.getPgProvider() == null ? "INICIS" : payment.getPgProvider().name())
                .paymentMethod(PaymentDefaults.PAYMENT_METHOD_CARD)
                .externalSendRequired(true)
                .settlementIncludedYn(false)
                .confirmedYn(confirmed)
                .confirmedAt(confirmed ? occurredAt : null)
                .build();
        SalesTransaction saved;
        try {
            saved = salesRepository.save(sales);
        } catch (DataIntegrityViolationException duplicate) {
            return salesRepository.findBySourceTypeAndSourceId(saleType.name(), sourceId)
                    .orElseThrow(() -> duplicate);
        }
        createLines(saved, saleType, occurredAt);
        return saved;
    }

    /**
     * 매출 헤더를 상품 단위 라인으로 분해한다. 주문이 없으면(순수 결제) 라인을 만들지 않는다.
     * SALE: 주문 상품별로 한 라인. CANCEL: 원 SALE 라인들에 취소액을 금액 비중대로 안분(음수).
     * 어느 경우든 마지막 라인이 반올림 잔액을 흡수해 라인 합계 = 헤더 saleAmount 가 정확히 성립한다.
     */
    private void createLines(SalesTransaction header, SaleType saleType, LocalDateTime occurredAt) {
        if (salesLineRepository.existsBySalesTransactionId(header.getId())) return;
        CommerceOrder order = commerceOrderRepository.findByOrderNo(header.getOrderNo()).orElse(null);
        if (order == null) return;

        List<LineSeed> seeds = SaleType.CANCEL.equals(saleType)
                ? cancelSeeds(order, header.getSaleAmount())
                : saleSeeds(order);
        if (seeds.isEmpty()) return;

        BigDecimal target = header.getSaleAmount();
        BigDecimal weightTotal = seeds.stream().map(LineSeed::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<SalesTransactionLine> lines = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < seeds.size(); i++) {
            LineSeed seed = seeds.get(i);
            BigDecimal lineAmount = (i == seeds.size() - 1 || weightTotal.signum() == 0)
                    ? target.subtract(allocated)
                    : target.multiply(seed.weight()).divide(weightTotal, 0, RoundingMode.HALF_UP);
            allocated = allocated.add(lineAmount);
            BigDecimal supply = calculateSupplyAmount(lineAmount.abs());
            if (lineAmount.signum() < 0) supply = supply.negate();
            lines.add(SalesTransactionLine.builder()
                    .salesTransactionId(header.getId())
                    .storeId(header.getStoreId())
                    .orderId(order.getId())
                    .orderItemId(seed.orderItemId())
                    .productId(seed.productId())
                    .productName(seed.productName())
                    .categoryName(seed.categoryName())
                    .sku(seed.sku())
                    .quantity(seed.quantity())
                    .saleType(saleType)
                    .lineAmount(lineAmount)
                    .supplyAmount(supply)
                    .vatAmount(lineAmount.subtract(supply))
                    .businessDate(header.getBusinessDate())
                    .occurredAt(occurredAt)
                    .confirmedYn(Boolean.TRUE.equals(header.getConfirmedYn()))
                    .build());
        }
        salesLineRepository.saveAll(lines);
    }

    private List<LineSeed> saleSeeds(CommerceOrder order) {
        List<CommerceOrderItem> items = commerceOrderItemRepository.findByOrderIdOrderByIdAsc(order.getId());
        if (items.isEmpty()) return List.of();
        Map<Long, Product> products = productRepository.findAllById(items.stream()
                        .map(CommerceOrderItem::getProductId).filter(java.util.Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<LineSeed> seeds = new ArrayList<>();
        for (CommerceOrderItem item : items) {
            Product product = item.getProductId() == null ? null : products.get(item.getProductId());
            seeds.add(new LineSeed(
                    item.getId(), item.getProductId(), item.getProductName(),
                    product == null || !StringUtils.hasText(product.getCategory()) ? "미분류" : product.getCategory().trim(),
                    item.getProductCode(), item.getQuantity(),
                    item.getItemAmount() == null ? BigDecimal.ZERO : item.getItemAmount().max(BigDecimal.ZERO)));
        }
        return seeds;
    }

    /** 취소 라인의 씨앗은 원 SALE 라인 — 그 금액 비중대로 취소액을 나눈다(수량은 원 라인 그대로 표기). */
    private List<LineSeed> cancelSeeds(CommerceOrder order, BigDecimal signedCancelAmount) {
        List<SalesTransactionLine> saleLines = salesLineRepository.findBySalesTransactionIdInOrderByIdAsc(
                salesRepository.findByOrderNoOrderByIdAsc(order.getOrderNo()).stream()
                        .filter(s -> SaleType.SALE.equals(s.getSaleType()))
                        .map(SalesTransaction::getId).toList());
        if (saleLines.isEmpty()) return List.of();
        return saleLines.stream()
                .map(l -> new LineSeed(l.getOrderItemId(), l.getProductId(), l.getProductName(),
                        l.getCategoryName(), l.getSku(), l.getQuantity(), l.getLineAmount().abs()))
                .toList();
    }

    private record LineSeed(Long orderItemId, Long productId, String productName, String categoryName,
                            String sku, int quantity, BigDecimal weight) {
    }

    private SalesLedgerSummaryResponse summarizeSalesLedger(
            LocalDate start,
            LocalDate end,
            String transactionType,
            String ledgerStatus,
            String settlementStatus,
            String keyword,
            Long storeId) {
        return summarizeSalesLedger(start, end, transactionType, ledgerStatus, settlementStatus, keyword, storeId, null);
    }

    private SalesLedgerSummaryResponse summarizeSalesLedger(
            LocalDate start,
            LocalDate end,
            String transactionType,
            String ledgerStatus,
            String settlementStatus,
            String keyword,
            Long storeId,
            Boolean confirmedYn) {
        return salesRepository.summarizeLedger(
                start,
                end,
                storeId,
                parseSaleType(transactionType),
                parseLedgerStatus(ledgerStatus),
                parseSettlementStatus(settlementStatus),
                confirmedYn,
                normalizeKeyword(keyword)
        );
    }

    private BigDecimal calculateSupplyAmount(BigDecimal totalAmount) {
        return totalAmount.multiply(BigDecimal.TEN)
                .divide(BigDecimal.valueOf(11), 0, RoundingMode.HALF_UP);
    }

    private Long findOriginalSaleId(String orderNo) {
        return salesRepository.findFirstByOrderNoAndSaleTypeOrderByIdAsc(orderNo, SaleType.SALE)
                .map(SalesTransaction::getId)
                .orElse(null);
    }

    private SaleType parseSaleType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return SaleType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new ValidationBusinessException(ErrorCode.SALES_INVALID_FILTER, "지원하지 않는 거래유형입니다. 허용 값: SALE, CANCEL, ADJUST");
        }
    }

    private LedgerStatus parseLedgerStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LedgerStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new ValidationBusinessException(ErrorCode.SALES_INVALID_FILTER, "지원하지 않는 원장상태입니다.");
        }
    }

    private SalesSettlementStatus parseSettlementStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return SalesSettlementStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new ValidationBusinessException(ErrorCode.SALES_INVALID_FILTER, "지원하지 않는 정산상태입니다.");
        }
    }

    private String normalizeKeyword(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim() : null;
    }

    private void saveAudit(String domainType, String actionType, String referenceKey, String description) {
        auditLogRepository.save(AuditLog.builder()
                .domainType(domainType)
                .actionType(actionType)
                .referenceKey(referenceKey)
                .description(description)
                .loggedAt(LocalDateTime.now())
                .build());
    }
}
