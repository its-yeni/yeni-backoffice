package com.yeni.backoffice.core.payment.service;

import com.yeni.backoffice.core.common.exception.BusinessException;
import com.yeni.backoffice.core.common.exception.ConflictException;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.NotFoundException;
import com.yeni.backoffice.core.common.exception.ValidationBusinessException;
import com.yeni.backoffice.core.commerce.service.CommerceOrderPaymentStateService;
import com.yeni.backoffice.core.payment.adapter.PaymentGatewayAdapter;
import com.yeni.backoffice.core.payment.adapter.PaymentGatewayAdapterResolver;
import com.yeni.backoffice.core.payment.dto.PaymentBridgeDtos.PaymentBridgeCancelRequest;
import com.yeni.backoffice.core.payment.dto.PaymentBridgeDtos.PaymentBridgeCancelResponse;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.PaymentCancelRequest;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.PaymentCancelResponse;
import com.yeni.backoffice.core.payment.entity.PaymentCancel;
import com.yeni.backoffice.core.payment.entity.PaymentTransaction;
import com.yeni.backoffice.core.payment.entity.SalesTransaction;
import com.yeni.backoffice.core.payment.enums.CancelStatus;
import com.yeni.backoffice.core.payment.enums.CancelType;
import com.yeni.backoffice.core.payment.enums.LogResultStatus;
import com.yeni.backoffice.core.payment.enums.PaymentEventType;
import com.yeni.backoffice.core.payment.enums.PaymentStatus;
import com.yeni.backoffice.core.payment.enums.PgApiType;
import com.yeni.backoffice.core.payment.enums.PgCompany;
import com.yeni.backoffice.core.payment.enums.PgProvider;
import com.yeni.backoffice.core.payment.enums.RecoveryType;
import com.yeni.backoffice.core.payment.enums.SaleType;
import com.yeni.backoffice.core.payment.gateway.PaymentGateway;
import com.yeni.backoffice.core.payment.gateway.PaymentGatewayRegistry;
import com.yeni.backoffice.core.payment.gateway.command.PaymentCancelCommand;
import com.yeni.backoffice.core.payment.gateway.result.PaymentCancelResult;
import com.yeni.backoffice.core.payment.repository.PaymentCancelRepository;
import com.yeni.backoffice.core.payment.repository.PaymentRecoveryTaskRepository;
import com.yeni.backoffice.core.payment.repository.PaymentTransactionRepository;
import com.yeni.backoffice.core.payment.support.PaymentAuditHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class PaymentCancelService {

    private static final PgCompany PG_COMPANY = PgCompany.INICIS;

    private final PaymentGatewayAdapterResolver adapterResolver;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final PaymentTransactionRepository paymentRepository;
    private final PaymentCancelRepository cancelRepository;
    private final PaymentRecoveryTaskRepository recoveryTaskRepository;
    private final SalesLedgerService salesLedgerService;
    private final PaymentNotificationService notificationService;
    private final PaymentRecoveryService recoveryService;
    private final PaymentAuditHelper auditHelper;
    private final CommerceOrderPaymentStateService orderStateService;

    public PaymentCancelService(
            PaymentGatewayAdapterResolver adapterResolver,
            PaymentGatewayRegistry gatewayRegistry,
            PaymentTransactionRepository paymentRepository,
            PaymentCancelRepository cancelRepository,
            PaymentRecoveryTaskRepository recoveryTaskRepository,
            SalesLedgerService salesLedgerService,
            PaymentNotificationService notificationService,
            PaymentRecoveryService recoveryService,
            PaymentAuditHelper auditHelper,
            CommerceOrderPaymentStateService orderStateService) {
        this.adapterResolver = adapterResolver;
        this.gatewayRegistry = gatewayRegistry;
        this.paymentRepository = paymentRepository;
        this.cancelRepository = cancelRepository;
        this.recoveryTaskRepository = recoveryTaskRepository;
        this.salesLedgerService = salesLedgerService;
        this.notificationService = notificationService;
        this.recoveryService = recoveryService;
        this.auditHelper = auditHelper;
        this.orderStateService = orderStateService;
    }

    @Transactional
    public PaymentBridgeCancelResponse cancelPaymentBridge(Long paymentId, PaymentBridgeCancelRequest request) {
        validateBridgeCancelRequest(request);
        PaymentTransaction payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PAYMENT_NOT_FOUND));

        String idempotencyKey = request.idempotencyKey().trim();
        PaymentCancel existingCancel = cancelRepository.findByCancelRequestKey(idempotencyKey).orElse(null);
        if (existingCancel != null) {
            orderStateService.syncCancellation(payment.getOrderNo(), payment.getCanceledAmount());
            return new PaymentBridgeCancelResponse(
                    existingCancel.getId(), payment.getId(), payment.getPgProvider(), payment.getTid(),
                    existingCancel.getCancelAmount(), existingCancel.getCancelType().name(),
                    payment.getPaymentStatus().name(), "IDEMPOTENT_REPLAY", "Existing cancel result returned.");
        }

        String cancelUnknownTaskKey = "CANCEL_UNKNOWN-" + idempotencyKey;
        if (recoveryTaskRepository.findByTaskKey(cancelUnknownTaskKey).isPresent()) {
            return new PaymentBridgeCancelResponse(null, payment.getId(), payment.getPgProvider(), payment.getTid(),
                    request.cancelAmount(), null, payment.getPaymentStatus().name(), "IDEMPOTENT_REPLAY",
                    "Existing unknown cancel recovery task returned.");
        }
        if (payment.isCancelCompleted()) {
            throw new ConflictException(ErrorCode.PAYMENT_ALREADY_CANCELED);
        }
        if (request.cancelAmount().compareTo(payment.getCancelableAmount()) > 0) {
            throw new ValidationBusinessException(ErrorCode.PAYMENT_CANCEL_AMOUNT_EXCEEDED);
        }

        PgProvider provider = request.pgProvider() == null ? payment.getPgProvider() : request.pgProvider();
        PaymentGateway gateway = gatewayRegistry.get(provider);
        PaymentCancelCommand command = new PaymentCancelCommand(provider, payment.getTid(), request.cancelAmount(), idempotencyKey, request.cancelReason());
        var apiLog = auditHelper.savePgLog(payment.getId(), payment.getOrderNo(), provider, PaymentEventType.CANCEL,
                PgApiType.CANCEL, idempotencyKey, command.toString(), LogResultStatus.REQUESTED, "PGB cancel requested");
        PaymentCancelResult result = gateway.cancel(command);
        apiLog.complete(result.toString(), result.success() ? LogResultStatus.SUCCESS : LogResultStatus.FAILED, result.resultMessage());

        if (result.unknown()) {
            payment.updateStatus(PaymentStatus.CANCEL_UNKNOWN, result.resultMessage());
            recoveryService.createRecoveryTask(payment.getId(), null, payment.getOrderNo(), payment.getTid(), idempotencyKey,
                    RecoveryType.CANCEL_UNKNOWN_CHECK, cancelUnknownTaskKey, result.resultMessage());
            auditHelper.saveAudit("PAYMENT", "CANCEL_UNKNOWN", payment.getOrderNo(), result.resultMessage());
            return new PaymentBridgeCancelResponse(null, payment.getId(), provider, payment.getTid(), request.cancelAmount(),
                    null, payment.getPaymentStatus().name(), result.resultCode(), result.resultMessage());
        }
        if (!result.success()) {
            payment.updateStatus(PaymentStatus.CANCEL_FAILED, result.resultMessage());
            auditHelper.saveAudit("PAYMENT", "CANCEL_FAILED", payment.getOrderNo(), result.resultMessage());
            throw new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED, "결제 취소에 실패했습니다: " + result.resultMessage());
        }

        CancelType cancelType = request.cancelAmount().compareTo(payment.getCancelableAmount()) == 0 ? CancelType.FULL : CancelType.PARTIAL;
        payment.cancel(request.cancelAmount());
        PaymentCancel cancel = PaymentCancel.builder()
                .paymentId(payment.getId()).tid(payment.getTid()).cancelRequestKey(idempotencyKey)
                .cancelAmount(request.cancelAmount()).cancelType(cancelType).cancelStatus(CancelStatus.SUCCESS)
                .canceledAt(LocalDateTime.now()).cancelReason(request.cancelReason()).build();
        cancelRepository.save(cancel);

        SalesTransaction sales = salesLedgerService.createSales(payment, SaleType.CANCEL, cancel.getId(), request.cancelAmount().negate(), cancel.getCanceledAt());
        notificationService.createExternalSendRequest(sales, "CANCEL-" + idempotencyKey);
        notificationService.createAlimtalkQueue(payment, sales, "CANCEL-" + idempotencyKey, "CANCEL");
        orderStateService.syncCancellation(payment.getOrderNo(), payment.getCanceledAmount());
        auditHelper.saveAudit("PAYMENT", "CANCELED", payment.getOrderNo(), "PGB cancel completed through " + provider);

        return new PaymentBridgeCancelResponse(cancel.getId(), payment.getId(), provider, payment.getTid(), cancel.getCancelAmount(),
                cancel.getCancelType().name(), payment.getPaymentStatus().name(), result.resultCode(), result.resultMessage());
    }

    @Transactional
    public PaymentCancelResponse cancelPayment(Long paymentId, PaymentCancelRequest request) {
        validateCancelRequest(request);
        PaymentTransaction payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PAYMENT_NOT_FOUND));

        PaymentCancel existingCancel = cancelRepository.findByCancelRequestKey(request.cancelRequestKey()).orElse(null);
        if (existingCancel != null) {
            orderStateService.syncCancellation(payment.getOrderNo(), payment.getCanceledAmount());
            return new PaymentCancelResponse(existingCancel.getId(), payment.getId(), payment.getTid(),
                    existingCancel.getCancelAmount(), existingCancel.getCancelType().name(), payment.getPaymentStatus().name());
        }
        if (payment.isCancelCompleted()) {
            throw new ConflictException(ErrorCode.PAYMENT_ALREADY_CANCELED);
        }
        if (request.cancelAmount().compareTo(payment.getCancelableAmount()) > 0) {
            throw new ValidationBusinessException(ErrorCode.PAYMENT_CANCEL_AMOUNT_EXCEEDED);
        }

        PaymentGatewayAdapter adapter = adapterResolver.resolve(PG_COMPANY);
        PaymentGatewayAdapter.CancelResult result = adapter.cancel(new PaymentGatewayAdapter.CancelRequest(
                payment.getTid(), request.cancelAmount(), request.cancelRequestKey()));
        var cancelLog = auditHelper.savePgLog(payment.getId(), payment.getOrderNo(), PgApiType.CANCEL,
                request.toString(), LogResultStatus.REQUESTED, "취소 mock 요청");
        if (!result.success()) {
            cancelLog.complete(result.toString(), LogResultStatus.FAILED, result.resultMessage());
            throw new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED, "PG 취소에 실패했습니다: " + result.resultMessage());
        }

        CancelType cancelType = request.cancelAmount().compareTo(payment.getCancelableAmount()) == 0 ? CancelType.FULL : CancelType.PARTIAL;
        payment.cancel(request.cancelAmount());
        PaymentCancel cancel = PaymentCancel.builder()
                .paymentId(payment.getId()).tid(payment.getTid()).cancelRequestKey(request.cancelRequestKey())
                .cancelAmount(request.cancelAmount()).cancelType(cancelType).cancelStatus(CancelStatus.SUCCESS)
                .canceledAt(LocalDateTime.now()).cancelReason(request.cancelReason()).build();
        cancelRepository.save(cancel);

        SalesTransaction sales = salesLedgerService.createSales(payment, SaleType.CANCEL, cancel.getId(), request.cancelAmount().negate(), cancel.getCanceledAt());
        notificationService.createExternalSendRequest(sales, "CANCEL-" + request.cancelRequestKey());
        notificationService.createAlimtalkQueue(payment, sales, "CANCEL-" + request.cancelRequestKey(), "CANCEL");
        orderStateService.syncCancellation(payment.getOrderNo(), payment.getCanceledAmount());
        cancelLog.complete(result.toString(), LogResultStatus.SUCCESS, result.resultMessage());
        auditHelper.saveAudit("PAYMENT", "CANCELED", payment.getOrderNo(), "PG 취소 완료 후 취소 매출 및 외부 전송 요청을 생성했습니다.");

        return new PaymentCancelResponse(cancel.getId(), payment.getId(), payment.getTid(),
                cancel.getCancelAmount(), cancel.getCancelType().name(), payment.getPaymentStatus().name());
    }

    private void validateBridgeCancelRequest(PaymentBridgeCancelRequest request) {
        if (request == null || request.cancelAmount() == null || request.cancelAmount().compareTo(BigDecimal.ZERO) <= 0
                || !StringUtils.hasText(request.idempotencyKey())) {
            throw new ValidationBusinessException(ErrorCode.PAYMENT_CANCEL_KEY_REQUIRED, "0보다 큰 취소금액과 취소 중복 방지 키는 필수입니다.");
        }
    }

    private void validateCancelRequest(PaymentCancelRequest request) {
        if (request == null || request.cancelAmount() == null || request.cancelAmount().compareTo(BigDecimal.ZERO) <= 0
                || !StringUtils.hasText(request.cancelRequestKey())) {
            throw new ValidationBusinessException(ErrorCode.PAYMENT_CANCEL_KEY_REQUIRED);
        }
    }
}
