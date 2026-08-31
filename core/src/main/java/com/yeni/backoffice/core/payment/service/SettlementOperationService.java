package com.yeni.backoffice.core.payment.service;

import com.yeni.backoffice.core.common.exception.ConflictException;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.NotFoundException;
import com.yeni.backoffice.core.payment.config.InicisStdPayProperties;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SettlementBatchRunRequest;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SettlementAdjustmentRequest;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SettlementDetailPageResponse;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SettlementDetailResponse;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SettlementFeeDetailResponse;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SettlementReconciliationResponse;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SettlementPayRequest;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SettlementLogResponse;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SettlementStatementResponse;
import com.yeni.backoffice.core.payment.dto.SalesAnalyticsDtos.SettlementCategoryRow;
import com.yeni.backoffice.core.payment.entity.SalesTransactionLine;
import com.yeni.backoffice.core.payment.entity.SalesTransaction;
import com.yeni.backoffice.core.payment.entity.SettlementDetail;
import com.yeni.backoffice.core.payment.entity.SettlementFeeDetail;
import com.yeni.backoffice.core.payment.entity.SettlementLog;
import com.yeni.backoffice.core.payment.entity.SettlementStatement;
import com.yeni.backoffice.core.payment.enums.SettlementStatus;
import com.yeni.backoffice.core.payment.enums.PgReconciliationResolutionStatus;
import com.yeni.backoffice.core.payment.repository.SalesTransactionLineRepository;
import com.yeni.backoffice.core.payment.repository.SalesTransactionRepository;
import com.yeni.backoffice.core.payment.repository.SettlementDetailRepository;
import com.yeni.backoffice.core.payment.repository.SettlementFeeDetailRepository;
import com.yeni.backoffice.core.payment.repository.SettlementLogRepository;
import com.yeni.backoffice.core.payment.repository.SettlementStatementRepository;
import com.yeni.backoffice.core.payment.repository.PgSettlementImportRepository;
import com.yeni.backoffice.core.payment.repository.PgSettlementReconciliationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class SettlementOperationService {

    private final InicisStdPayProperties inicisProperties;
    private final SalesTransactionRepository salesRepository;
    private final SettlementStatementRepository settlementStatementRepository;
    private final SettlementDetailRepository settlementDetailRepository;
    private final SettlementFeeDetailRepository settlementFeeDetailRepository;
    private final SettlementLogRepository settlementLogRepository;
    private final SettlementBatchProcessor settlementBatchProcessor;
    private final PgSettlementImportRepository pgSettlementImportRepository;
    private final PgSettlementReconciliationRepository pgReconciliationRepository;
    private final SalesTransactionLineRepository salesLineRepository;
    private final com.yeni.backoffice.core.commerce.repository.CommerceStoreRepository commerceStoreRepository;
    private final ConcurrentMap<String, ReentrantLock> executionLocks = new ConcurrentHashMap<>();

    public SettlementOperationService(
            InicisStdPayProperties inicisProperties,
            SalesTransactionRepository salesRepository,
            SettlementStatementRepository settlementStatementRepository,
            SettlementDetailRepository settlementDetailRepository,
            SettlementFeeDetailRepository settlementFeeDetailRepository,
            SettlementLogRepository settlementLogRepository,
            SettlementBatchProcessor settlementBatchProcessor,
            PgSettlementImportRepository pgSettlementImportRepository,
            PgSettlementReconciliationRepository pgReconciliationRepository,
            SalesTransactionLineRepository salesLineRepository,
            com.yeni.backoffice.core.commerce.repository.CommerceStoreRepository commerceStoreRepository) {
        this.inicisProperties = inicisProperties;
        this.salesLineRepository = salesLineRepository;
        this.commerceStoreRepository = commerceStoreRepository;
        this.salesRepository = salesRepository;
        this.settlementStatementRepository = settlementStatementRepository;
        this.settlementDetailRepository = settlementDetailRepository;
        this.settlementFeeDetailRepository = settlementFeeDetailRepository;
        this.settlementLogRepository = settlementLogRepository;
        this.settlementBatchProcessor = settlementBatchProcessor;
        this.pgSettlementImportRepository = pgSettlementImportRepository;
        this.pgReconciliationRepository = pgReconciliationRepository;
    }

    public SettlementStatementResponse runDailySettlement(SettlementBatchRunRequest request) {
        LocalDate targetDate = request == null || request.targetDate() == null
                ? LocalDate.now()
                : request.targetDate();
        String mid = inicisProperties.getMid();
        Long storeId = request == null ? null : request.storeId();
        String lockKey = targetDate + ":" + mid + ":" + storeId;
        ReentrantLock executionLock = executionLocks.computeIfAbsent(lockKey, ignored -> new ReentrantLock());

        if (!executionLock.tryLock()) {
            throw duplicateExecution(targetDate, mid);
        }

        try {
            SettlementStatement existingStatement = storeId == null
                    ? settlementStatementRepository.findBySettlementDateAndMid(targetDate, mid).orElse(null)
                    : settlementStatementRepository.findBySettlementDateAndMidAndStoreId(targetDate, mid, storeId).orElse(null);
            if (existingStatement != null && !SettlementStatus.DRAFT.equals(existingStatement.getSettlementStatus())) {
                throw duplicateExecution(targetDate, mid);
            }
            return storeId == null
                    ? settlementBatchProcessor.process(targetDate, mid, existingStatement)
                    : settlementBatchProcessor.process(targetDate, mid, existingStatement, storeId);
        } catch (DataIntegrityViolationException duplicate) {
            throw duplicateExecution(targetDate, mid);
        } finally {
            executionLock.unlock();
            executionLocks.remove(lockKey, executionLock);
        }
    }

    @Transactional(readOnly = true)
    public List<SettlementStatementResponse> getStatements(LocalDate startDate, LocalDate endDate) {
        return getStatements(startDate, endDate, null);
    }

    @Transactional(readOnly = true)
    public List<SettlementStatementResponse> getStatements(LocalDate startDate, LocalDate endDate, Long storeId) {
        LocalDate start = startDate == null ? LocalDate.now().minusDays(30) : startDate;
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        List<SettlementStatement> statements = storeId == null
                ? settlementStatementRepository.findBySettlementDateBetweenOrderBySettlementDateDesc(start, end)
                : settlementStatementRepository.findBySettlementDateBetweenAndStoreIdOrderBySettlementDateDesc(start, end, storeId);
        return statements.stream()
                .map(this::statementResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SettlementDetailPageResponse getStatement(Long statementId) {
        SettlementStatement statement = settlementStatementRepository.findById(statementId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SETTLEMENT_NOT_FOUND));
        List<SettlementDetail> detailEntities = settlementDetailRepository.findBySettlementStatementIdOrderByIdAsc(statementId);
        List<SettlementFeeDetail> feeEntities = settlementFeeDetailRepository.findBySettlementStatementIdOrderByIdAsc(statementId);
        List<SettlementDetailResponse> details = detailEntities.stream()
                .map(SettlementDetailResponse::from)
                .toList();
        List<SettlementFeeDetailResponse> feeDetails = feeEntities.stream()
                .map(SettlementFeeDetailResponse::from)
                .toList();
        return new SettlementDetailPageResponse(
                SettlementStatementResponse.from(statement, detailEntities),
                details,
                feeDetails,
                reconcile(statement, detailEntities, feeEntities),
                settlementLogRepository.findBySettlementStatementIdOrderByLoggedAtDesc(statementId).stream()
                        .map(SettlementLogResponse::from)
                        .toList(),
                categoryBreakdown(detailEntities)
        );
    }

    /**
     * 정산 명세를 분류별로 집계한다. 정산 명세는 결제 단위이므로, 각 명세의 매출·수수료·정산액을
     * 그 매출 헤더의 상품 라인들에 금액 비중대로 안분해 categoryName 별로 합산한다.
     * 매출 라인이 없는(주문 없는 순수 결제) 명세는 "미분류"로 모은다.
     */
    private List<SettlementCategoryRow> categoryBreakdown(List<SettlementDetail> details) {
        if (details.isEmpty()) return List.of();
        Map<Long, List<SalesTransactionLine>> linesBySales = salesLineRepository
                .findBySalesTransactionIdInOrderByIdAsc(details.stream().map(SettlementDetail::getSalesId).toList())
                .stream().collect(java.util.stream.Collectors.groupingBy(SalesTransactionLine::getSalesTransactionId));

        Map<String, BigDecimal[]> acc = new LinkedHashMap<>(); // [sale, fee, net]
        for (SettlementDetail detail : details) {
            List<SalesTransactionLine> lines = linesBySales.getOrDefault(detail.getSalesId(), List.of());
            BigDecimal headerAmount = lines.stream().map(SalesTransactionLine::getLineAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (lines.isEmpty() || headerAmount.signum() == 0) {
                addTo(acc, "미분류", detail.getSaleAmount(), detail.getFeeAmount(), detail.getNetAmount());
                continue;
            }
            BigDecimal sale = BigDecimal.ZERO, fee = BigDecimal.ZERO, net = BigDecimal.ZERO;
            for (int i = 0; i < lines.size(); i++) {
                SalesTransactionLine line = lines.get(i);
                boolean last = i == lines.size() - 1;
                BigDecimal w = line.getLineAmount();
                BigDecimal saleShare = last ? detail.getSaleAmount().subtract(sale)
                        : detail.getSaleAmount().multiply(w).divide(headerAmount, 0, RoundingMode.HALF_UP);
                BigDecimal feeShare = last ? detail.getFeeAmount().subtract(fee)
                        : detail.getFeeAmount().multiply(w).divide(headerAmount, 0, RoundingMode.HALF_UP);
                BigDecimal netShare = last ? detail.getNetAmount().subtract(net)
                        : detail.getNetAmount().multiply(w).divide(headerAmount, 0, RoundingMode.HALF_UP);
                sale = sale.add(saleShare);
                fee = fee.add(feeShare);
                net = net.add(netShare);
                addTo(acc, line.getCategoryName(), saleShare, feeShare, netShare);
            }
        }
        return acc.entrySet().stream()
                .map(e -> new SettlementCategoryRow(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .sorted((a, b) -> b.saleAmount().compareTo(a.saleAmount()))
                .toList();
    }

    private void addTo(Map<String, BigDecimal[]> acc, String key, BigDecimal sale, BigDecimal fee, BigDecimal net) {
        BigDecimal[] v = acc.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
        v[0] = v[0].add(sale);
        v[1] = v[1].add(fee);
        v[2] = v[2].add(net);
    }

    /** 매장별 정산 요약 — 매장이 "우리 매장이 얼마 정산받았는지"를 확인하는 집계. */
    @Transactional(readOnly = true)
    public List<com.yeni.backoffice.core.payment.dto.SalesAnalyticsDtos.StoreSettlementSummary> getStoreSummary(
            LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate == null ? LocalDate.now().minusDays(30) : startDate;
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        List<SettlementStatement> statements = settlementStatementRepository
                .findBySettlementDateBetweenOrderBySettlementDateDesc(start, end);
        Map<Long, String> storeNames = commerceStoreRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.yeni.backoffice.core.commerce.entity.CommerceStore::getId,
                        com.yeni.backoffice.core.commerce.entity.CommerceStore::getStoreName, (a, b) -> a));

        record Acc(long[] counts, BigDecimal[] amounts) {}
        Map<Long, Acc> byStore = new LinkedHashMap<>();
        for (SettlementStatement s : statements) {
            Long key = s.getStoreId();
            Acc acc = byStore.computeIfAbsent(key, k -> new Acc(new long[4],
                    new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO}));
            acc.counts()[0]++;
            acc.amounts()[0] = acc.amounts()[0].add(nz(s.getGrossAmount()));
            acc.amounts()[1] = acc.amounts()[1].add(nz(s.getFeeAmount()));
            acc.amounts()[2] = acc.amounts()[2].add(nz(s.getVatAmount()));
            acc.amounts()[3] = acc.amounts()[3].add(nz(s.getNetAmount()));
            switch (s.getSettlementStatus()) {
                case DRAFT -> acc.counts()[1]++;
                case CONFIRMED -> acc.counts()[2]++;
                case PAID -> { acc.counts()[3]++; acc.amounts()[4] = acc.amounts()[4].add(nz(s.getNetAmount())); }
            }
        }
        return byStore.entrySet().stream()
                .map(e -> new com.yeni.backoffice.core.payment.dto.SalesAnalyticsDtos.StoreSettlementSummary(
                        e.getKey(),
                        e.getKey() == null ? "매장 미지정" : storeNames.getOrDefault(e.getKey(), "매장 #" + e.getKey()),
                        (int) e.getValue().counts()[0],
                        e.getValue().amounts()[0], e.getValue().amounts()[1], e.getValue().amounts()[2],
                        e.getValue().amounts()[3], e.getValue().amounts()[4],
                        (int) e.getValue().counts()[1], (int) e.getValue().counts()[2], (int) e.getValue().counts()[3]))
                .sorted((a, b) -> b.netAmount().compareTo(a.netAmount()))
                .toList();
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    @Transactional
    public SettlementStatementResponse confirmStatement(Long statementId) {
        SettlementStatement statement = settlementStatementRepository.findById(statementId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SETTLEMENT_NOT_FOUND));
        if (!SettlementStatus.DRAFT.equals(statement.getSettlementStatus())) {
            throw new ConflictException(ErrorCode.SETTLEMENT_INVALID_STATUS, "작성 중인 정산명세만 확정할 수 있습니다.");
        }
        List<SettlementDetail> details = settlementDetailRepository.findBySettlementStatementIdOrderByIdAsc(statementId);
        List<SettlementFeeDetail> fees = settlementFeeDetailRepository.findBySettlementStatementIdOrderByIdAsc(statementId);
        SettlementReconciliationResponse reconciliation = reconcile(statement, details, fees);
        if (!reconciliation.confirmable()) {
            throw new ConflictException(
                    ErrorCode.SETTLEMENT_INVALID_STATUS,
                    "정산 대사 불일치로 확정할 수 없습니다. " + String.join(" ", reconciliation.blockingReasons())
            );
        }
        pgSettlementImportRepository.findFirstByBusinessDateAndMidOrderByIdDesc(statement.getSettlementDate(), statement.getMid())
                .ifPresent(batch -> {
                    long unresolved = pgReconciliationRepository.findByImportIdOrderById(batch.getId()).stream()
                            .filter(row -> row.getResolutionStatus() == PgReconciliationResolutionStatus.OPEN
                                    || row.getResolutionStatus() == PgReconciliationResolutionStatus.IN_REVIEW)
                            .count();
                    if (unresolved > 0) {
                        throw new ConflictException(ErrorCode.SETTLEMENT_INVALID_STATUS,
                                "PG 대사 미해결 건 " + unresolved + "건이 남아 있어 정산을 확정할 수 없습니다.");
                    }
                });
        details.forEach(detail -> salesRepository.findById(detail.getSalesId()).ifPresent(SalesTransaction::markSettled));
        statement.confirm();
        saveSettlementLog(statementId, "CONFIRM", "SUCCESS", "정산 확정 완료");
        return statementResponse(statement);
    }

    @Transactional
    public SettlementStatementResponse applyAdjustment(Long statementId, SettlementAdjustmentRequest request) {
        SettlementStatement statement = settlementStatementRepository.findById(statementId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SETTLEMENT_NOT_FOUND));
        if (!SettlementStatus.DRAFT.equals(statement.getSettlementStatus())) {
            throw new ConflictException(ErrorCode.SETTLEMENT_INVALID_STATUS, "초안 상태에서만 정산 금액을 조정할 수 있습니다.");
        }
        BigDecimal adjustment = request == null || request.adjustmentAmount() == null ? BigDecimal.ZERO : request.adjustmentAmount();
        BigDecimal hold = request == null || request.holdAmount() == null ? BigDecimal.ZERO : request.holdAmount();
        if (hold.signum() < 0) {
            throw new ConflictException(ErrorCode.SETTLEMENT_INVALID_STATUS, "지급 보류 금액은 0원 이상이어야 합니다.");
        }
        BigDecimal baseNet = statement.getGrossAmount().subtract(statement.getFeeAmount()).subtract(statement.getVatAmount());
        if (baseNet.add(adjustment).subtract(hold).signum() < 0) {
            throw new ConflictException(ErrorCode.SETTLEMENT_INVALID_STATUS, "조정 후 최종 지급액은 0원 미만일 수 없습니다.");
        }
        statement.applyAdjustment(adjustment, hold, request == null ? null : request.scheduledPayoutDate());
        String reason = request != null && StringUtils.hasText(request.reason()) ? request.reason().trim() : "조정 사유 미입력";
        saveSettlementLog(statementId, "ADJUST", "SUCCESS",
                "가감 " + adjustment.toPlainString() + "원, 보류 " + hold.toPlainString() + "원 · " + reason);
        return statementResponse(statement);
    }

    @Transactional
    public SettlementStatementResponse markPaid(Long statementId, SettlementPayRequest request) {
        SettlementStatement statement = settlementStatementRepository.findById(statementId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SETTLEMENT_NOT_FOUND));
        if (!SettlementStatus.CONFIRMED.equals(statement.getSettlementStatus())) {
            throw new ConflictException(ErrorCode.SETTLEMENT_INVALID_STATUS, "확정된 정산명세만 지급 처리할 수 있습니다.");
        }
        settlementDetailRepository.findBySettlementStatementIdOrderByIdAsc(statementId)
                .forEach(detail -> salesRepository.findById(detail.getSalesId()).ifPresent(SalesTransaction::markPaid));
        if (request == null || !StringUtils.hasText(request.payoutReference())) {
            throw new ConflictException(ErrorCode.SETTLEMENT_INVALID_STATUS, "지급 참조번호를 입력해야 지급 완료 처리할 수 있습니다.");
        }
        statement.markPaid(
                request.payoutReference().trim(),
                StringUtils.hasText(request.payoutAccountMasked()) ? request.payoutAccountMasked().trim() : null,
                LocalDateTime.now()
        );
        saveSettlementLog(statementId, "PAY", "SUCCESS", "정산 지급 처리 완료");
        return statementResponse(statement);
    }

    private SettlementStatementResponse statementResponse(SettlementStatement statement) {
        return SettlementStatementResponse.from(
                statement,
                settlementDetailRepository.findBySettlementStatementIdOrderByIdAsc(statement.getId())
        );
    }

    private SettlementReconciliationResponse reconcile(
            SettlementStatement statement,
            List<SettlementDetail> details,
            List<SettlementFeeDetail> fees) {
        BigDecimal ledgerGross = details.stream()
                .map(SettlementDetail::getSaleAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal policyFee = fees.stream()
                .map(SettlementFeeDetail::getFeeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal policyVat = fees.stream()
                .map(SettlementFeeDetail::getVatAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal calculatedNet = ledgerGross.subtract(policyFee).subtract(policyVat)
                .add(zeroIfNull(statement.getAdjustmentAmount()))
                .subtract(zeroIfNull(statement.getHoldAmount()));
        BigDecimal grossDifference = ledgerGross.subtract(statement.getGrossAmount());
        BigDecimal feeDifference = policyFee.subtract(statement.getFeeAmount());
        BigDecimal vatDifference = policyVat.subtract(statement.getVatAmount());
        BigDecimal netDifference = calculatedNet.subtract(statement.getNetAmount());
        boolean ledgerMatched = isZero(grossDifference);
        boolean feeMatched = isZero(feeDifference) && isZero(vatDifference);
        boolean netMatched = isZero(netDifference);
        List<String> reasons = new ArrayList<>();
        if (details.isEmpty()) reasons.add("포함된 매출 원장이 없습니다.");
        if (!ledgerMatched) reasons.add("원장 합계와 정산 총액이 일치하지 않습니다.");
        if (!feeMatched) reasons.add("수수료 정책 합계와 정산 수수료가 일치하지 않습니다.");
        if (!netMatched) reasons.add("재계산한 지급액과 정산 지급액이 일치하지 않습니다.");
        return new SettlementReconciliationResponse(
                ledgerGross, statement.getGrossAmount(), grossDifference,
                policyFee, statement.getFeeAmount(), feeDifference,
                policyVat, statement.getVatAmount(), vatDifference,
                calculatedNet, statement.getNetAmount(), netDifference,
                details.size(), ledgerMatched, feeMatched, netMatched,
                reasons.isEmpty(), List.copyOf(reasons)
        );
    }

    private boolean isZero(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) == 0;
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private ConflictException duplicateExecution(LocalDate targetDate, String mid) {
        return new ConflictException(
                ErrorCode.SETTLEMENT_DUPLICATE_EXECUTION,
                "동일 정산일과 MID 기준의 정산 배치가 이미 실행 중이거나 완료되었습니다. targetDate="
                        + targetDate + ", mid=" + mid
        );
    }

    private void saveSettlementLog(Long statementId, String actionType, String resultStatus, String message) {
        settlementLogRepository.save(SettlementLog.builder()
                .settlementStatementId(statementId)
                .actionType(actionType)
                .resultStatus(resultStatus)
                .message(message)
                .loggedAt(LocalDateTime.now())
                .build());
    }
}
