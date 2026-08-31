package com.yeni.backoffice.core.payment.service;

import com.yeni.backoffice.core.common.exception.BusinessException;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.payment.config.InicisStdPayProperties;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SettlementBatchRunRequest;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SettlementStatementResponse;
import com.yeni.backoffice.core.payment.repository.PgSettlementImportRepository;
import com.yeni.backoffice.core.payment.repository.PgSettlementReconciliationRepository;
import com.yeni.backoffice.core.payment.repository.SalesTransactionRepository;
import com.yeni.backoffice.core.payment.repository.SettlementDetailRepository;
import com.yeni.backoffice.core.payment.repository.SettlementFeeDetailRepository;
import com.yeni.backoffice.core.payment.repository.SettlementLogRepository;
import com.yeni.backoffice.core.payment.repository.SettlementStatementRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SettlementOperationServiceTest {

    @Test
    void concurrentRunForSameDateAndMidIsRejectedImmediately() throws Exception {
        InicisStdPayProperties properties = mock(InicisStdPayProperties.class);
        SettlementStatementRepository statementRepository = mock(SettlementStatementRepository.class);
        SettlementBatchProcessor processor = mock(SettlementBatchProcessor.class);
        when(properties.getMid()).thenReturn("MID-TEST");
        when(statementRepository.findBySettlementDateAndMid(LocalDate.of(2026, 6, 9), "MID-TEST"))
                .thenReturn(Optional.empty());

        CountDownLatch processorStarted = new CountDownLatch(1);
        CountDownLatch releaseProcessor = new CountDownLatch(1);
        SettlementStatementResponse response = new SettlementStatementResponse(
                1L,
                LocalDate.of(2026, 6, 9),
                "INICIS",
                "MID-TEST",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "DRAFT",
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
        when(processor.process(LocalDate.of(2026, 6, 9), "MID-TEST", null)).thenAnswer(invocation -> {
            processorStarted.countDown();
            releaseProcessor.await(5, TimeUnit.SECONDS);
            return response;
        });

        SettlementOperationService service = new SettlementOperationService(
                properties,
                mock(SalesTransactionRepository.class),
                statementRepository,
                mock(SettlementDetailRepository.class),
                mock(SettlementFeeDetailRepository.class),
                mock(SettlementLogRepository.class),
                processor,
                mock(PgSettlementImportRepository.class),
                mock(PgSettlementReconciliationRepository.class),
                mock(com.yeni.backoffice.core.payment.repository.SalesTransactionLineRepository.class),
                mock(com.yeni.backoffice.core.commerce.repository.CommerceStoreRepository.class)
        );
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<SettlementStatementResponse> firstRun = executor.submit(
                    () -> service.runDailySettlement(new SettlementBatchRunRequest(LocalDate.of(2026, 6, 9))));
            assertThat(processorStarted.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> service.runDailySettlement(new SettlementBatchRunRequest(LocalDate.of(2026, 6, 9))))
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SETTLEMENT_DUPLICATE_EXECUTION));

            releaseProcessor.countDown();
            assertThat(firstRun.get(5, TimeUnit.SECONDS)).isEqualTo(response);
        } finally {
            releaseProcessor.countDown();
            executor.shutdownNow();
        }
    }
}
