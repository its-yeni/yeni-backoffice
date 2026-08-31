package com.yeni.backoffice.core.payment.service;

import com.yeni.backoffice.core.payment.adapter.PaymentGatewayAdapterResolver;
import com.yeni.backoffice.core.commerce.service.CommerceOrderPaymentStateService;
import com.yeni.backoffice.core.payment.config.InicisStdPayProperties;
import com.yeni.backoffice.core.payment.dto.PaymentBridgeDtos.PaymentApproveRequest;
import com.yeni.backoffice.core.payment.dto.PaymentBridgeDtos.PaymentApproveResponse;
import com.yeni.backoffice.core.payment.entity.PgApiLog;
import com.yeni.backoffice.core.payment.entity.PaymentTransaction;
import com.yeni.backoffice.core.payment.entity.SalesTransaction;
import com.yeni.backoffice.core.payment.enums.PaymentStatus;
import com.yeni.backoffice.core.payment.enums.PgProvider;
import com.yeni.backoffice.core.payment.enums.RecoveryType;
import com.yeni.backoffice.core.payment.enums.SaleType;
import com.yeni.backoffice.core.payment.gateway.PaymentGateway;
import com.yeni.backoffice.core.payment.gateway.PaymentGatewayRegistry;
import com.yeni.backoffice.core.payment.gateway.PaymentGatewayRouter;
import com.yeni.backoffice.core.payment.gateway.result.PaymentApproveResult;
import com.yeni.backoffice.core.payment.repository.PaymentAuthSessionRepository;
import com.yeni.backoffice.core.payment.repository.PaymentTransactionRepository;
import com.yeni.backoffice.core.payment.service.PaymentNotificationService;
import com.yeni.backoffice.core.payment.service.PaymentRecoveryService;
import com.yeni.backoffice.core.payment.support.PaymentAuditHelper;
import com.yeni.backoffice.core.payment.util.InicisSignatureService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentApproveServiceTest {

    private PaymentApproveService buildService(
            InicisStdPayProperties props,
            PaymentGatewayRegistry gatewayRegistry,
            PaymentGatewayRouter gatewayRouter,
            PaymentTransactionRepository paymentRepository,
            SalesLedgerService salesLedgerService,
            PaymentNotificationService notificationService,
            PaymentRecoveryService recoveryService,
            PaymentAuditHelper auditHelper) {
        return new PaymentApproveService(
                props,
                mock(InicisSignatureService.class),
                mock(PaymentGatewayAdapterResolver.class),
                gatewayRegistry,
                gatewayRouter,
                mock(PaymentAuthSessionRepository.class),
                paymentRepository,
                salesLedgerService,
                notificationService,
                recoveryService,
                auditHelper,
                mock(CommerceOrderPaymentStateService.class)
        );
    }

    @Test
    void approvePayment_whenIdempotencyKeyExists_returnsCachedResultWithoutCallingPg() {
        PaymentTransactionRepository paymentRepository = mock(PaymentTransactionRepository.class);
        PaymentGatewayRegistry gatewayRegistry = mock(PaymentGatewayRegistry.class);

        PaymentTransaction existing = PaymentTransaction.builder()
                .id(1L)
                .mid("MOCK_MID")
                .pgProvider(PgProvider.MOCK)
                .orderNo("ORDER-001")
                .tid("TID-001")
                .approvalRequestKey("APPROVE-ORDER-001")
                .approvedAmount(BigDecimal.valueOf(12_000))
                .canceledAmount(BigDecimal.ZERO)
                .currency("KRW")
                .paymentStatus(PaymentStatus.APPROVED)
                .approvedAt(LocalDateTime.now())
                .build();
        when(paymentRepository.findByApprovalRequestKey("APPROVE-ORDER-001")).thenReturn(Optional.of(existing));

        PaymentApproveService service = buildService(
                mock(InicisStdPayProperties.class), gatewayRegistry, mock(PaymentGatewayRouter.class),
                paymentRepository, mock(SalesLedgerService.class),
                mock(PaymentNotificationService.class), mock(PaymentRecoveryService.class), mock(PaymentAuditHelper.class));

        PaymentApproveRequest request = new PaymentApproveRequest(
                PgProvider.MOCK, "ORDER-001", BigDecimal.valueOf(12_000), "KRW",
                "테스트구매자", "테스트상품", "APPROVE-ORDER-001", "WEB", "PORTFOLIO", "카드");

        PaymentApproveResponse response = service.approvePayment(request);

        assertThat(response.resultCode()).isEqualTo("IDEMPOTENT_REPLAY");
        assertThat(response.paymentStatus()).isEqualTo("APPROVED");
        verify(gatewayRegistry, never()).get(any());
    }

    @Test
    void approvePayment_whenPgApproveSucceeds_savesPaymentAndCreatesSalesRecord() {
        PaymentTransactionRepository paymentRepository = mock(PaymentTransactionRepository.class);
        PaymentGatewayRegistry gatewayRegistry = mock(PaymentGatewayRegistry.class);
        PaymentGatewayRouter gatewayRouter = mock(PaymentGatewayRouter.class);
        SalesLedgerService salesLedgerService = mock(SalesLedgerService.class);
        PaymentNotificationService notificationService = mock(PaymentNotificationService.class);
        PaymentRecoveryService recoveryService = mock(PaymentRecoveryService.class);
        PaymentAuditHelper auditHelper = mock(PaymentAuditHelper.class);
        PaymentGateway mockGateway = mock(PaymentGateway.class);
        PgApiLog mockApiLog = mock(PgApiLog.class);

        InicisStdPayProperties props = mock(InicisStdPayProperties.class);
        when(props.getMid()).thenReturn("MOCK_MID");
        when(paymentRepository.findByApprovalRequestKey(any())).thenReturn(Optional.empty());
        when(paymentRepository.findByOrderNo(any())).thenReturn(Optional.empty());
        when(gatewayRouter.route(any(), any(), any(), any())).thenReturn(PgProvider.MOCK);
        when(gatewayRegistry.get(PgProvider.MOCK)).thenReturn(mockGateway);
        when(auditHelper.savePgLog(any(), any(), any(PgProvider.class), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockApiLog);
        when(mockGateway.approve(any())).thenReturn(
                new PaymentApproveResult(PgProvider.MOCK, true, PaymentStatus.APPROVED,
                        "TID-002", "0000", "성공", LocalDateTime.now(), false));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(salesLedgerService.createSales(any(), any(), any(), any(), any()))
                .thenReturn(mock(SalesTransaction.class));

        PaymentApproveService service = buildService(props, gatewayRegistry, gatewayRouter,
                paymentRepository, salesLedgerService, notificationService, recoveryService, auditHelper);

        PaymentApproveRequest request = new PaymentApproveRequest(
                PgProvider.MOCK, "ORDER-002", BigDecimal.valueOf(12_000), "KRW",
                "테스트구매자", "테스트상품", null, "WEB", "PORTFOLIO", "카드");

        PaymentApproveResponse response = service.approvePayment(request);

        assertThat(response.paymentStatus()).isEqualTo("APPROVED");
        verify(paymentRepository).save(any(PaymentTransaction.class));
        verify(salesLedgerService).createSales(any(), eq(SaleType.SALE), any(), eq(BigDecimal.valueOf(12_000)), any());
    }

    @Test
    void approvePayment_whenPgResultIsUnknown_savesApproveUnknownAndCreatesRecoveryTask() {
        PaymentTransactionRepository paymentRepository = mock(PaymentTransactionRepository.class);
        PaymentGatewayRegistry gatewayRegistry = mock(PaymentGatewayRegistry.class);
        PaymentGatewayRouter gatewayRouter = mock(PaymentGatewayRouter.class);
        PaymentNotificationService notificationService = mock(PaymentNotificationService.class);
        PaymentRecoveryService recoveryService = mock(PaymentRecoveryService.class);
        PaymentAuditHelper auditHelper = mock(PaymentAuditHelper.class);
        PaymentGateway mockGateway = mock(PaymentGateway.class);
        PgApiLog mockApiLog = mock(PgApiLog.class);

        InicisStdPayProperties props = mock(InicisStdPayProperties.class);
        when(props.getMid()).thenReturn("MOCK_MID");
        when(paymentRepository.findByApprovalRequestKey(any())).thenReturn(Optional.empty());
        when(paymentRepository.findByOrderNo(any())).thenReturn(Optional.empty());
        when(gatewayRouter.route(any(), any(), any(), any())).thenReturn(PgProvider.MOCK);
        when(gatewayRegistry.get(PgProvider.MOCK)).thenReturn(mockGateway);
        when(auditHelper.savePgLog(any(), any(), any(PgProvider.class), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockApiLog);
        when(mockGateway.approve(any())).thenReturn(
                new PaymentApproveResult(PgProvider.MOCK, false, PaymentStatus.APPROVE_UNKNOWN,
                        null, "9999", "결과불명", LocalDateTime.now(), true));
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            PaymentTransaction p = inv.getArgument(0);
            return PaymentTransaction.builder()
                    .id(99L)
                    .mid(p.getMid()).pgProvider(p.getPgProvider()).orderNo(p.getOrderNo())
                    .tid(p.getTid()).approvalRequestKey(p.getApprovalRequestKey())
                    .approvedAmount(p.getApprovedAmount()).canceledAmount(BigDecimal.ZERO)
                    .currency(p.getCurrency()).paymentStatus(p.getPaymentStatus())
                    .approvedAt(p.getApprovedAt()).failureReason(p.getFailureReason())
                    .build();
        });
        when(recoveryService.createRecoveryTask(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(true);

        PaymentApproveService service = buildService(props, gatewayRegistry, gatewayRouter,
                paymentRepository, mock(SalesLedgerService.class), notificationService, recoveryService, auditHelper);

        PaymentApproveRequest request = new PaymentApproveRequest(
                PgProvider.MOCK, "ORDER-003", BigDecimal.valueOf(12_000), "KRW",
                "테스트구매자", "테스트상품", null, "WEB", "PORTFOLIO", "카드");

        PaymentApproveResponse response = service.approvePayment(request);

        assertThat(response.paymentStatus()).isEqualTo("APPROVE_UNKNOWN");
        verify(recoveryService).createRecoveryTask(
                eq(99L), isNull(), eq("ORDER-003"), any(), any(),
                eq(RecoveryType.APPROVE_UNKNOWN_CHECK), any(), any());
    }
}
