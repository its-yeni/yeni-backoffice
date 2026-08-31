package com.yeni.backoffice.core.payment.service;

import com.yeni.backoffice.core.payment.config.InicisStdPayProperties;
import com.yeni.backoffice.core.payment.support.PaymentDefaults;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SettlementStatementResponse;
import com.yeni.backoffice.core.payment.entity.PgFeePolicy;
import com.yeni.backoffice.core.payment.entity.SalesTransaction;
import com.yeni.backoffice.core.payment.entity.SettlementBatchLog;
import com.yeni.backoffice.core.payment.entity.SettlementDetail;
import com.yeni.backoffice.core.payment.entity.SettlementFeeDetail;
import com.yeni.backoffice.core.payment.entity.SettlementLog;
import com.yeni.backoffice.core.payment.entity.SettlementStatement;
import com.yeni.backoffice.core.payment.enums.BatchStatus;
import com.yeni.backoffice.core.payment.enums.SettlementStatus;
import com.yeni.backoffice.core.payment.repository.PgFeePolicyRepository;
import com.yeni.backoffice.core.payment.repository.SalesTransactionRepository;
import com.yeni.backoffice.core.payment.repository.SettlementBatchLogRepository;
import com.yeni.backoffice.core.payment.repository.SettlementDetailRepository;
import com.yeni.backoffice.core.payment.repository.SettlementFeeDetailRepository;
import com.yeni.backoffice.core.payment.repository.SettlementLogRepository;
import com.yeni.backoffice.core.payment.repository.SettlementStatementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SettlementBatchProcessor {

    private final InicisStdPayProperties inicisProperties;
    private final SalesTransactionRepository salesRepository;
    private final PgFeePolicyRepository feePolicyRepository;
    private final SettlementStatementRepository settlementStatementRepository;
    private final SettlementDetailRepository settlementDetailRepository;
    private final SettlementFeeDetailRepository settlementFeeDetailRepository;
    private final SettlementBatchLogRepository batchLogRepository;
    private final SettlementLogRepository settlementLogRepository;

    public SettlementBatchProcessor(
            InicisStdPayProperties inicisProperties,
            SalesTransactionRepository salesRepository,
            PgFeePolicyRepository feePolicyRepository,
            SettlementStatementRepository settlementStatementRepository,
            SettlementDetailRepository settlementDetailRepository,
            SettlementFeeDetailRepository settlementFeeDetailRepository,
            SettlementBatchLogRepository batchLogRepository,
            SettlementLogRepository settlementLogRepository) {
        this.inicisProperties = inicisProperties;
        this.salesRepository = salesRepository;
        this.feePolicyRepository = feePolicyRepository;
        this.settlementStatementRepository = settlementStatementRepository;
        this.settlementDetailRepository = settlementDetailRepository;
        this.settlementFeeDetailRepository = settlementFeeDetailRepository;
        this.batchLogRepository = batchLogRepository;
        this.settlementLogRepository = settlementLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SettlementStatementResponse process(LocalDate targetDate, String mid, SettlementStatement existingStatement) {
        return process(targetDate, mid, existingStatement, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SettlementStatementResponse process(
            LocalDate targetDate, String mid, SettlementStatement existingStatement, Long storeId) {
        List<SalesTransaction> sales = storeId == null
                ? salesRepository.findByBusinessDateAndConfirmedYnTrueAndSettlementIncludedYnFalseOrderByIdAsc(targetDate)
                : salesRepository.findByBusinessDateAndStoreIdAndConfirmedYnTrueAndSettlementIncludedYnFalseOrderByIdAsc(targetDate, storeId);
        if (existingStatement != null && sales.isEmpty()) {
            return statementResponse(existingStatement);
        }

        SettlementBatchLog batchLog = batchLogRepository.save(SettlementBatchLog.builder()
                .targetDate(targetDate)
                .batchStatus(BatchStatus.RUNNING)
                .targetCount(sales.size())
                .successCount(0)
                .failureCount(0)
                .startedAt(LocalDateTime.now())
                .build());

        PgFeePolicy feePolicy = findFeePolicy(targetDate);
        BigDecimal pendingGrossAmount = sales.stream()
                .map(SalesTransaction::getSaleAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossAmount = existingStatement == null
                ? pendingGrossAmount
                : existingStatement.getGrossAmount().add(pendingGrossAmount);
        BigDecimal feeAmount = calculateFee(grossAmount.abs(), feePolicy.getFeeRate());
        BigDecimal vatAmount = feeAmount.divide(BigDecimal.TEN, 2, RoundingMode.HALF_UP);
        BigDecimal netAmount = grossAmount.subtract(feeAmount).subtract(vatAmount);

        SettlementStatement statement;
        if (existingStatement == null) {
            statement = settlementStatementRepository.saveAndFlush(SettlementStatement.builder()
                    .storeId(storeId)
                    .settlementDate(targetDate)
                    .pgCompany("INICIS")
                    .mid(mid)
                    .grossAmount(grossAmount)
                    .feeAmount(feeAmount)
                    .vatAmount(vatAmount)
                    .netAmount(netAmount)
                    .settlementStatus(SettlementStatus.DRAFT)
                    .build());
        } else {
            existingStatement.recalculate(grossAmount, feeAmount, vatAmount, netAmount);
            statement = settlementStatementRepository.saveAndFlush(existingStatement);
        }

        for (SalesTransaction sale : sales) {
            BigDecimal saleFee = calculateFee(sale.getSaleAmount().abs(), feePolicy.getFeeRate());
            settlementDetailRepository.save(SettlementDetail.builder()
                    .settlementStatementId(statement.getId())
                    .salesId(sale.getId())
                    .saleType(sale.getSaleType().name())
                    .saleAmount(sale.getSaleAmount())
                    .feeAmount(saleFee)
                    .netAmount(sale.getSaleAmount().subtract(saleFee))
                    .build());
            sale.markIncludedInSettlement();
        }

        List<SettlementFeeDetail> existingFeeDetails =
                settlementFeeDetailRepository.findBySettlementStatementIdOrderByIdAsc(statement.getId());
        if (existingFeeDetails.isEmpty()) {
            settlementFeeDetailRepository.save(SettlementFeeDetail.builder()
                    .settlementStatementId(statement.getId())
                    .feePolicyId(feePolicy.getId())
                    .feeRate(feePolicy.getFeeRate())
                    .feeAmount(feeAmount)
                    .vatAmount(vatAmount)
                    .build());
        } else {
            existingFeeDetails.get(0).recalculate(feeAmount, vatAmount);
        }

        batchLog.complete(sales.size(), 0);
        settlementLogRepository.save(SettlementLog.builder()
                .settlementStatementId(statement.getId())
                .actionType("BATCH_RUN")
                .resultStatus("SUCCESS")
                .message("정산 초안 생성 완료")
                .loggedAt(LocalDateTime.now())
                .build());
        return statementResponse(statement);
    }

    private SettlementStatementResponse statementResponse(SettlementStatement statement) {
        return SettlementStatementResponse.from(
                statement,
                settlementDetailRepository.findBySettlementStatementIdOrderByIdAsc(statement.getId())
        );
    }

    private PgFeePolicy findFeePolicy(LocalDate targetDate) {
        return feePolicyRepository
                .findFirstByPgCompanyAndMidAndPaymentMethodAndUseYnTrueAndEffectiveStartDateLessThanEqualAndEffectiveEndDateGreaterThanEqual(
                        "INICIS",
                        inicisProperties.getMid(),
                        PaymentDefaults.PAYMENT_METHOD_CARD,
                        targetDate,
                        targetDate
                )
                .orElseGet(() -> feePolicyRepository.save(PgFeePolicy.builder()
                        .pgCompany("INICIS")
                        .mid(inicisProperties.getMid())
                        .paymentMethod(PaymentDefaults.PAYMENT_METHOD_CARD)
                        .feeRate(new BigDecimal("0.0250"))
                        .effectiveStartDate(LocalDate.of(2020, 1, 1))
                        .effectiveEndDate(LocalDate.of(2099, 12, 31))
                        .useYn(true)
                        .build()));
    }

    private BigDecimal calculateFee(BigDecimal amount, BigDecimal feeRate) {
        return amount.multiply(feeRate).setScale(2, RoundingMode.HALF_UP);
    }
}
