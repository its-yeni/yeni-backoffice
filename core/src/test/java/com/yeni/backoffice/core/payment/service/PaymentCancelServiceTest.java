package com.yeni.backoffice.core.payment.service;

import com.yeni.backoffice.core.common.exception.ValidationBusinessException;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.commerce.service.CommerceOrderPaymentStateService;
import com.yeni.backoffice.core.payment.adapter.PaymentGatewayAdapterResolver;
import com.yeni.backoffice.core.payment.dto.PaymentBridgeDtos.PaymentBridgeCancelRequest;
import com.yeni.backoffice.core.payment.dto.PaymentBridgeDtos.PaymentBridgeCancelResponse;
import com.yeni.backoffice.core.payment.entity.PaymentCancel;
import com.yeni.backoffice.core.payment.entity.PaymentTransaction;
import com.yeni.backoffice.core.payment.entity.PgApiLog;
import com.yeni.backoffice.core.payment.entity.SalesTransaction;
import com.yeni.backoffice.core.payment.enums.CancelStatus;
import com.yeni.backoffice.core.payment.enums.CancelType;
import com.yeni.backoffice.core.payment.enums.PaymentStatus;
import com.yeni.backoffice.core.payment.enums.PgProvider;
import com.yeni.backoffice.core.payment.gateway.PaymentGateway;
import com.yeni.backoffice.core.payment.gateway.PaymentGatewayRegistry;
import com.yeni.backoffice.core.payment.gateway.result.PaymentCancelResult;
import com.yeni.backoffice.core.payment.repository.PaymentCancelRepository;
import com.yeni.backoffice.core.payment.repository.PaymentRecoveryTaskRepository;
import com.yeni.backoffice.core.payment.repository.PaymentTransactionRepository;
import com.yeni.backoffice.core.payment.support.PaymentAuditHelper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentCancelServiceTest {

    private PaymentCancelService buildService(
            PaymentGatewayRegistry gatewayRegistry,
            PaymentTransactionRepository paymentRepository,
            PaymentCancelRepository cancelRepository,
            PaymentRecoveryTaskRepository recoveryTaskRepository,
            SalesLedgerService salesLedgerService,
            PaymentNotificationService notificationService,
            PaymentRecoveryService recoveryService,
            PaymentAuditHelper auditHelper) {
        return new PaymentCancelService(
                mock(PaymentGatewayAdapterResolver.class),
                gatewayRegistry,
                paymentRepository,
                cancelRepository,
                recoveryTaskRepository,
                salesLedgerService,
                notificationService,
                recoveryService,
                auditHelper,
                mock(CommerceOrderPaymentStateService.class)
        );
    }

    private PaymentTransaction approvedPayment(BigDecimal approved, BigDecimal cancelled) {
        return PaymentTransaction.builder()
                .id(1L)
                .mid("MOCK_MID")
                .pgProvider(PgProvider.MOCK)
                .orderNo("ORDER-CANCEL-001")
                .tid("TID-CANCEL-001")
                .approvedAmount(approved)
                .canceledAmount(cancelled)
                .currency("KRW")
                .paymentStatus(PaymentStatus.APPROVED)
                .approvedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void cancelPaymentBridge_whenCancelAmountExceedsRemaining_throwsValidationException() {
        PaymentTransactionRepository paymentRepository = mock(PaymentTransactionRepository.class);
        PaymentCancelRepository cancelRepository = mock(PaymentCancelRepository.class);
        PaymentRecoveryTaskRepository recoveryTaskRepository = mock(PaymentRecoveryTaskRepository.class);
        PaymentGatewayRegistry gatewayRegistry = mock(PaymentGatewayRegistry.class);

        when(paymentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(approvedPayment(BigDecimal.valueOf(10_000), BigDecimal.ZERO)));
        when(cancelRepository.findByCancelRequestKey("KEY-001")).thenReturn(Optional.empty());
        when(recoveryTaskRepository.findByTaskKey(any())).thenReturn(Optional.empty());

        PaymentCancelService service = buildService(gatewayRegistry, paymentRepository, cancelRepository,
                recoveryTaskRepository, mock(SalesLedgerService.class),
                mock(PaymentNotificationService.class), mock(PaymentRecoveryService.class), mock(PaymentAuditHelper.class));

        PaymentBridgeCancelRequest request = new PaymentBridgeCancelRequest(
                PgProvider.MOCK, BigDecimal.valueOf(15_000), "초과취소테스트", "KEY-001");

        assertThatThrownBy(() -> service.cancelPaymentBridge(1L, request))
                .isInstanceOfSatisfying(ValidationBusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_CANCEL_AMOUNT_EXCEEDED));
        verify(gatewayRegistry, never()).get(any());
    }

    @Test
    void cancelPaymentBridge_whenIdempotencyKeyExists_returnsCachedCancelWithoutCallingPg() {
        PaymentTransactionRepository paymentRepository = mock(PaymentTransactionRepository.class);
        PaymentCancelRepository cancelRepository = mock(PaymentCancelRepository.class);
        PaymentRecoveryTaskRepository recoveryTaskRepository = mock(PaymentRecoveryTaskRepository.class);
        PaymentGatewayRegistry gatewayRegistry = mock(PaymentGatewayRegistry.class);

        PaymentTransaction payment = approvedPayment(BigDecimal.valueOf(12_000), BigDecimal.ZERO);
        PaymentCancel existing = PaymentCancel.builder()
                .id(50L)
                .paymentId(1L)
                .tid("TID-CANCEL-001")
                .cancelRequestKey("KEY-002")
                .cancelAmount(BigDecimal.valueOf(12_000))
                .cancelType(CancelType.FULL)
                .cancelStatus(CancelStatus.SUCCESS)
                .canceledAt(LocalDateTime.now())
                .build();

        when(paymentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(payment));
        when(cancelRepository.findByCancelRequestKey("KEY-002")).thenReturn(Optional.of(existing));

        PaymentCancelService service = buildService(gatewayRegistry, paymentRepository, cancelRepository,
                recoveryTaskRepository, mock(SalesLedgerService.class),
                mock(PaymentNotificationService.class), mock(PaymentRecoveryService.class), mock(PaymentAuditHelper.class));

        PaymentBridgeCancelRequest request = new PaymentBridgeCancelRequest(
                PgProvider.MOCK, BigDecimal.valueOf(12_000), "중복취소테스트", "KEY-002");

        PaymentBridgeCancelResponse response = service.cancelPaymentBridge(1L, request);

        assertThat(response.resultCode()).isEqualTo("IDEMPOTENT_REPLAY");
        assertThat(response.cancelId()).isEqualTo(50L);
        verify(gatewayRegistry, never()).get(any());
    }

    @Test
    void cancelPaymentBridge_whenPartialCancel_createsCancelWithTypePartial() {
        PaymentTransactionRepository paymentRepository = mock(PaymentTransactionRepository.class);
        PaymentCancelRepository cancelRepository = mock(PaymentCancelRepository.class);
        PaymentRecoveryTaskRepository recoveryTaskRepository = mock(PaymentRecoveryTaskRepository.class);
        PaymentGatewayRegistry gatewayRegistry = mock(PaymentGatewayRegistry.class);
        SalesLedgerService salesLedgerService = mock(SalesLedgerService.class);
        PaymentNotificationService notificationService = mock(PaymentNotificationService.class);
        PaymentRecoveryService recoveryService = mock(PaymentRecoveryService.class);
        PaymentAuditHelper auditHelper = mock(PaymentAuditHelper.class);
        PaymentGateway mockGateway = mock(PaymentGateway.class);
        PgApiLog mockApiLog = mock(PgApiLog.class);

        when(paymentRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(approvedPayment(BigDecimal.valueOf(12_000), BigDecimal.ZERO)));
        when(cancelRepository.findByCancelRequestKey("KEY-003")).thenReturn(Optional.empty());
        when(recoveryTaskRepository.findByTaskKey(any())).thenReturn(Optional.empty());
        when(gatewayRegistry.get(any())).thenReturn(mockGateway);
        when(auditHelper.savePgLog(any(), any(), any(PgProvider.class), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockApiLog);
        when(mockGateway.cancel(any())).thenReturn(
                new PaymentCancelResult(PgProvider.MOCK, true, PaymentStatus.PARTIAL_CANCELED, "0000", "부분취소성공", false));
        when(salesLedgerService.createSales(any(), any(), any(), any(), any()))
                .thenReturn(mock(SalesTransaction.class));

        PaymentCancelService service = buildService(gatewayRegistry, paymentRepository, cancelRepository,
                recoveryTaskRepository, salesLedgerService, notificationService, recoveryService, auditHelper);

        PaymentBridgeCancelRequest request = new PaymentBridgeCancelRequest(
                PgProvider.MOCK, BigDecimal.valueOf(5_000), "부분취소테스트", "KEY-003");

        PaymentBridgeCancelResponse response = service.cancelPaymentBridge(1L, request);

        assertThat(response.cancelType()).isEqualTo(CancelType.PARTIAL.name());
        assertThat(response.cancelAmount()).isEqualByComparingTo(BigDecimal.valueOf(5_000));
    }
}
