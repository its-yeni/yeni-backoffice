package com.yeni.backoffice.core.payment.service;

import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.NotFoundException;
import com.yeni.backoffice.core.payment.dto.PaymentBridgeDtos.PaymentQueryResponse;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.PaymentResponse;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.PgLogResponse;
import com.yeni.backoffice.core.payment.entity.PaymentTransaction;
import com.yeni.backoffice.core.payment.entity.SalesTransaction;
import com.yeni.backoffice.core.payment.enums.LogResultStatus;
import com.yeni.backoffice.core.payment.enums.PaymentEventType;
import com.yeni.backoffice.core.payment.enums.PaymentStatus;
import com.yeni.backoffice.core.payment.enums.PgApiType;
import com.yeni.backoffice.core.payment.enums.SaleType;
import com.yeni.backoffice.core.payment.gateway.PaymentGateway;
import com.yeni.backoffice.core.payment.gateway.PaymentGatewayRegistry;
import com.yeni.backoffice.core.payment.gateway.command.PaymentQueryCommand;
import com.yeni.backoffice.core.payment.gateway.result.PaymentQueryResult;
import com.yeni.backoffice.core.payment.repository.PaymentRecoveryTaskRepository;
import com.yeni.backoffice.core.payment.repository.PaymentTransactionRepository;
import com.yeni.backoffice.core.payment.repository.PgApiLogRepository;
import com.yeni.backoffice.core.payment.repository.SalesTransactionRepository;
import com.yeni.backoffice.core.payment.support.PaymentAuditHelper;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PaymentQueryService {

    private final PaymentGatewayRegistry gatewayRegistry;
    private final PaymentTransactionRepository paymentRepository;
    private final PaymentRecoveryTaskRepository recoveryTaskRepository;
    private final PgApiLogRepository pgApiLogRepository;
    private final SalesLedgerService salesLedgerService;
    private final PaymentNotificationService notificationService;
    private final PaymentRecoveryService recoveryService;
    private final PaymentAuditHelper auditHelper;
    private final SalesTransactionRepository salesTransactionRepository;

    public PaymentQueryService(
            PaymentGatewayRegistry gatewayRegistry,
            PaymentTransactionRepository paymentRepository,
            PaymentRecoveryTaskRepository recoveryTaskRepository,
            PgApiLogRepository pgApiLogRepository,
            SalesLedgerService salesLedgerService,
            PaymentNotificationService notificationService,
            PaymentRecoveryService recoveryService,
            PaymentAuditHelper auditHelper,
            SalesTransactionRepository salesTransactionRepository) {
        this.gatewayRegistry = gatewayRegistry;
        this.paymentRepository = paymentRepository;
        this.recoveryTaskRepository = recoveryTaskRepository;
        this.pgApiLogRepository = pgApiLogRepository;
        this.salesLedgerService = salesLedgerService;
        this.notificationService = notificationService;
        this.recoveryService = recoveryService;
        this.auditHelper = auditHelper;
        this.salesTransactionRepository = salesTransactionRepository;
    }

    @Transactional
    public PaymentQueryResponse retryQuery(Long paymentId) {
        PaymentTransaction payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PAYMENT_NOT_FOUND));
        PaymentGateway gateway = gatewayRegistry.get(payment.getPgProvider());
        PaymentQueryCommand command = new PaymentQueryCommand(payment.getPgProvider(), payment.getTid(), payment.getOrderNo());
        var apiLog = auditHelper.savePgLog(payment.getId(), payment.getOrderNo(), payment.getPgProvider(), PaymentEventType.QUERY,
                PgApiType.INQUIRY, null, command.toString(), LogResultStatus.REQUESTED, "PGB query requested");
        PaymentQueryResult result = gateway.query(command);
        apiLog.complete(result.toString(), result.success() ? LogResultStatus.SUCCESS : LogResultStatus.FAILED, result.resultMessage());

        if (result.success() && (PaymentStatus.UNKNOWN.equals(payment.getPaymentStatus())
                || PaymentStatus.APPROVE_UNKNOWN.equals(payment.getPaymentStatus()))) {
            payment.updateStatus(result.paymentStatus(), null);
            if (PaymentStatus.APPROVED.equals(result.paymentStatus())) {
                SalesTransaction sales = salesLedgerService.createSales(payment, SaleType.SALE, payment.getId(), payment.getApprovedAmount(), payment.getApprovedAt());
                notificationService.createExternalSendRequest(sales, "SALE-" + payment.getOrderNo());
                notificationService.createAlimtalkQueue(payment, sales, "SALE-" + payment.getOrderNo(), "APPROVE");
                recoveryService.markRecoverySuccess("APPROVE_UNKNOWN-" + payment.getOrderNo());
                auditHelper.saveAudit("PAYMENT", "APPROVE_UNKNOWN_RECOVERED", payment.getOrderNo(),
                        "PG query confirmed approve success and created missing sales/follow-up records.");
            }
        } else if (!result.success() && (PaymentStatus.UNKNOWN.equals(payment.getPaymentStatus())
                || PaymentStatus.APPROVE_UNKNOWN.equals(payment.getPaymentStatus()))) {
            payment.updateStatus(PaymentStatus.APPROVE_FAILED, result.resultMessage());
            recoveryTaskRepository.findByTaskKey("APPROVE_UNKNOWN-" + payment.getOrderNo())
                    .ifPresent(task -> task.markFailed(result.resultMessage()));
            auditHelper.saveAudit("PAYMENT", "APPROVE_UNKNOWN_FAILED", payment.getOrderNo(),
                    "PG query confirmed approve failure. SALE ledger was not created.");
        }
        return PaymentQueryResponse.from(payment, payment.getPgProvider(), result.resultCode(), result.resultMessage());
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPayments() {
        return getPayments(null);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPayments(Long storeId) {
        return getPayments(storeId, false);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPayments(Long storeId, boolean includeUnassigned) {
        return paymentRepository.findAll(Sort.by(Sort.Direction.DESC, "approvedAt")).stream()
                .filter(payment -> storeId == null || storeId.equals(payment.getStoreId()) || includeUnassigned && payment.getStoreId() == null)
                .map(PaymentResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public PaymentResponse assignStore(Long paymentId, Long storeId) {
        PaymentTransaction payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PAYMENT_NOT_FOUND));
        List<SalesTransaction> sales = salesTransactionRepository.findByPaymentIdOrderByIdAsc(paymentId);
        if (sales.stream().anyMatch(item -> Boolean.TRUE.equals(item.getSettlementIncludedYn()))) {
            throw new IllegalStateException("정산에 포함된 거래는 매장을 변경할 수 없습니다.");
        }
        payment.assignStore(storeId);
        sales.forEach(item -> item.assignStore(storeId));
        auditHelper.saveAudit("PAYMENT", "STORE_ASSIGNED", payment.getOrderNo(), "storeId=" + storeId);
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<PgLogResponse> getPaymentLogs(Long paymentId) {
        PaymentTransaction payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PAYMENT_NOT_FOUND));
        return pgApiLogRepository.findByOrderNoOrderByLoggedAtDesc(payment.getOrderNo()).stream()
                .map(PgLogResponse::from)
                .collect(Collectors.toList());
    }
}
