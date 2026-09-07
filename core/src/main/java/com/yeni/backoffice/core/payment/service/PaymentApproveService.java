package com.yeni.backoffice.core.payment.service;

import com.yeni.backoffice.core.common.exception.BusinessException;
import com.yeni.backoffice.core.common.exception.ConflictException;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.ValidationBusinessException;
import com.yeni.backoffice.core.commerce.service.CommerceOrderPaymentStateService;
import com.yeni.backoffice.core.commerce.repository.CommerceOrderRepository;
import com.yeni.backoffice.core.payment.adapter.PaymentGatewayAdapter;
import com.yeni.backoffice.core.payment.adapter.PaymentGatewayAdapterResolver;
import com.yeni.backoffice.core.payment.config.InicisStdPayProperties;
import com.yeni.backoffice.core.payment.dto.PaymentBridgeDtos.PaymentApproveRequest;
import com.yeni.backoffice.core.payment.dto.PaymentBridgeDtos.PaymentApproveResponse;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.InicisApproveResponse;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.InicisAuthResultRequest;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.InicisReadyRequest;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.InicisReadyResponse;
import com.yeni.backoffice.core.payment.entity.PaymentAuthSession;
import com.yeni.backoffice.core.payment.entity.PaymentTransaction;
import com.yeni.backoffice.core.payment.entity.SalesTransaction;
import com.yeni.backoffice.core.payment.enums.LogResultStatus;
import com.yeni.backoffice.core.payment.enums.PaymentAuthStatus;
import com.yeni.backoffice.core.payment.enums.PaymentEventType;
import com.yeni.backoffice.core.payment.enums.PaymentStatus;
import com.yeni.backoffice.core.payment.enums.PgApiType;
import com.yeni.backoffice.core.payment.enums.PgCompany;
import com.yeni.backoffice.core.payment.enums.PgProvider;
import com.yeni.backoffice.core.payment.enums.RecoveryType;
import com.yeni.backoffice.core.payment.enums.SaleType;
import com.yeni.backoffice.core.payment.gateway.PaymentGateway;
import com.yeni.backoffice.core.payment.gateway.PaymentGatewayRegistry;
import com.yeni.backoffice.core.payment.gateway.PaymentGatewayRouter;
import com.yeni.backoffice.core.payment.gateway.command.PaymentApproveCommand;
import com.yeni.backoffice.core.payment.gateway.result.PaymentApproveResult;
import com.yeni.backoffice.core.payment.repository.PaymentAuthSessionRepository;
import com.yeni.backoffice.core.payment.repository.PaymentTransactionRepository;
import com.yeni.backoffice.core.payment.support.PaymentAuditHelper;
import com.yeni.backoffice.core.payment.support.PaymentDefaults;
import com.yeni.backoffice.core.payment.util.InicisSignatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentApproveService {

    private static final Logger log = LoggerFactory.getLogger(PaymentApproveService.class);
    private static final PgCompany PG_COMPANY = PgCompany.INICIS;

    private final InicisStdPayProperties inicisProperties;
    private final InicisSignatureService signatureService;
    private final PaymentGatewayAdapterResolver adapterResolver;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final PaymentGatewayRouter gatewayRouter;
    private final PaymentAuthSessionRepository authSessionRepository;
    private final PaymentTransactionRepository paymentRepository;
    private final SalesLedgerService salesLedgerService;
    private final PaymentNotificationService notificationService;
    private final PaymentRecoveryService recoveryService;
    private final PaymentAuditHelper auditHelper;
    private final CommerceOrderPaymentStateService orderStateService;
    private final CommerceOrderRepository orderRepository;
    private final PaymentApprovalValidator approvalValidator;

    public PaymentApproveService(
            InicisStdPayProperties inicisProperties,
            InicisSignatureService signatureService,
            PaymentGatewayAdapterResolver adapterResolver,
            PaymentGatewayRegistry gatewayRegistry,
            PaymentGatewayRouter gatewayRouter,
            PaymentAuthSessionRepository authSessionRepository,
            PaymentTransactionRepository paymentRepository,
            SalesLedgerService salesLedgerService,
            PaymentNotificationService notificationService,
            PaymentRecoveryService recoveryService,
            PaymentAuditHelper auditHelper,
            CommerceOrderPaymentStateService orderStateService,
            CommerceOrderRepository orderRepository,
            PaymentApprovalValidator approvalValidator) {
        this.inicisProperties = inicisProperties;
        this.signatureService = signatureService;
        this.adapterResolver = adapterResolver;
        this.gatewayRegistry = gatewayRegistry;
        this.gatewayRouter = gatewayRouter;
        this.authSessionRepository = authSessionRepository;
        this.paymentRepository = paymentRepository;
        this.salesLedgerService = salesLedgerService;
        this.notificationService = notificationService;
        this.recoveryService = recoveryService;
        this.auditHelper = auditHelper;
        this.orderStateService = orderStateService;
        this.orderRepository = orderRepository;
        this.approvalValidator = approvalValidator;
    }

    @Transactional
    public PaymentApproveResponse approvePayment(PaymentApproveRequest request) {
        approvalValidator.validateApproveRequest(request);
        String idempotencyKey = defaultText(request.idempotencyKey(), "APPROVE-" + request.orderNo());
        orderStateService.validateApproval(request.orderNo(), request.amount());

        PaymentTransaction existingPayment = paymentRepository.findByApprovalRequestKey(idempotencyKey)
                .or(() -> paymentRepository.findByOrderNo(request.orderNo()))
                .orElse(null);
        if (existingPayment != null) {
            orderStateService.markApproved(existingPayment.getOrderNo(), existingPayment.getId(), existingPayment.getTid(), "기존 결제 승인 결과를 반환했습니다.");
            return toApproveResponse(existingPayment, existingPayment.getPgProvider(), "IDEMPOTENT_REPLAY", "Existing approve result returned.");
        }


        PgProvider provider = gatewayRouter.route(
                request.pgProvider(),
                request.channelType(),
                request.storeCode(),
                defaultText(request.paymentMethod(), PaymentDefaults.PAYMENT_METHOD_CARD)
        );
        PaymentGateway gateway = gatewayRegistry.get(provider);
        PaymentApproveCommand command = new PaymentApproveCommand(
                provider,
                midByProvider(provider),
                request.orderNo(),
                request.amount(),
                defaultCurrency(request.currency()),
                idempotencyKey,
                defaultText(request.channelType(), "WEB"),
                defaultText(request.storeCode(), "PORTFOLIO"),
                defaultText(request.paymentMethod(), PaymentDefaults.PAYMENT_METHOD_CARD),
                resolveStoreId(request.orderNo(), request.storeId()),
                request.productName()
        );

        long startedAt = System.currentTimeMillis();
        PaymentApproveResult result = gateway.approve(command);
        // 최종 성공/실패가 이미 결정된 상태로 독립 트랜잭션에 기록한다 — 아래에서 실패를 이유로
        // 예외를 던져도(트랜잭션 롤백) 이 로그는 남아야 운영자가 원인을 추적할 수 있다.
        auditHelper.recordPgApiLog(null, request.orderNo(), provider, PaymentEventType.APPROVE,
                PgApiType.APPROVE, idempotencyKey, command.toString(), result.toString(),
                result.success() ? LogResultStatus.SUCCESS : LogResultStatus.FAILED, result.resultMessage(), result.tid());

        if (result.unknown()) {
            PaymentTransaction payment = saveUnknownPayment(command, result);
            orderStateService.markUnknown(payment.getOrderNo(), payment.getId(), payment.getTid(), result.resultMessage());
            recoveryService.createRecoveryTask(payment.getId(), null, payment.getOrderNo(), payment.getTid(), idempotencyKey,
                    RecoveryType.APPROVE_UNKNOWN_CHECK, "APPROVE_UNKNOWN-" + payment.getOrderNo(), result.resultMessage());
            auditHelper.saveAudit("PAYMENT", "UNKNOWN", request.orderNo(), "PGB approve result is unknown. retry-query is required.");
            return toApproveResponse(payment, provider, result.resultCode(), result.resultMessage());
        }
        if (!result.success()) {
            auditHelper.saveAudit("PAYMENT", "APPROVE_FAILED", request.orderNo(), result.resultMessage());
            orderStateService.markFailedAndRestore(request.orderNo(), result.resultMessage());
            throw new BusinessException(ErrorCode.PAYMENT_APPROVE_FAILED, "결제 승인에 실패했습니다: " + result.resultMessage());
        }

        PaymentTransaction payment = PaymentTransaction.builder()
                .storeId(command.storeId())
                .mid(command.mid())
                .pgProvider(provider)
                .orderNo(command.orderNo())
                .productName(command.productName())
                .tid(result.tid())
                .approvalRequestKey(idempotencyKey)
                .approvedAmount(command.amount())
                .canceledAmount(BigDecimal.ZERO)
                .currency(command.currency())
                .paymentMethod(command.paymentMethod())
                .paymentStatus(PaymentStatus.APPROVED)
                .approvedAt(result.approvedAt())
                .build();
        try {
            paymentRepository.save(payment);
            // 시나리오 데모 전용 훅: orderNo에 NETCANCEL 마커가 있으면 "PG 승인은 성공했지만
            // 이후 내부 처리가 실패"하는 상황을 의도적으로 재현해 아래 catch의 망취소/복구 흐름을 탄다.
            if (command.orderNo() != null && command.orderNo().toUpperCase().contains("NETCANCEL")) {
                throw new IllegalStateException("[시나리오] 승인 후 내부 처리 실패(망취소) 상황을 시뮬레이션합니다.");
            }
            SalesTransaction sales = salesLedgerService.createSales(payment, SaleType.SALE, payment.getId(), payment.getApprovedAmount(), result.approvedAt());
            notificationService.createExternalSendRequest(sales, "SALE-" + payment.getOrderNo());
            notificationService.createAlimtalkQueue(payment, sales, "SALE-" + payment.getOrderNo(), "APPROVE");
            orderStateService.markApproved(payment.getOrderNo(), payment.getId(), payment.getTid(), result.resultMessage());
            auditHelper.saveAudit("PAYMENT", "APPROVED", payment.getOrderNo(), "PGB approve completed through " + provider);
            return toApproveResponse(payment, provider, result.resultCode(), result.resultMessage() + " (" + (System.currentTimeMillis() - startedAt) + "ms)");
        } catch (RuntimeException internalFailure) {
            RecoveryType recoveryType = payment.getId() == null
                    ? RecoveryType.APPROVE_INTERNAL_SAVE_FAILED
                    : RecoveryType.NETWORK_CANCEL;
            String taskKey = recoveryType.name() + "-" + command.orderNo();
            if (payment.getId() != null) {
                payment.updateStatus(PaymentStatus.NETWORK_CANCEL_REQUIRED, internalFailure.getMessage());
            }
            boolean recoveryRecorded = recoveryService.createRecoveryTask(payment.getId(), null, command.orderNo(), result.tid(), idempotencyKey,
                    recoveryType, taskKey, internalFailure.getMessage());
            log.error("PG approve succeeded but internal processing failed. recoveryRecorded={}, recoveryType={}, taskKey={}, orderNo={}, tid={}",
                    recoveryRecorded, recoveryType, taskKey, command.orderNo(), result.tid(), internalFailure);
            auditHelper.saveAudit("PAYMENT", "APPROVE_INTERNAL_FAILED", request.orderNo(),
                    "PG approve succeeded but internal processing failed: " + internalFailure.getMessage());
            return toApproveResponse(payment, provider, "NETWORK_CANCEL_REQUIRED",
                    "PG 승인 후 내부 처리 실패로 망취소/복구 대상에 등록했습니다.");
        }
    }

    @Transactional
    public InicisReadyResponse prepareStdPay(InicisReadyRequest request) {
        approvalValidator.validateReadyRequest(request);
        authSessionRepository.findByOrderNo(request.orderNo())
                .ifPresent(session -> {
                    throw new ConflictException(ErrorCode.PAYMENT_DUPLICATED_REQUEST, "이미 결제 세션이 생성된 주문번호입니다.");
                });

        String authToken = UUID.randomUUID().toString();
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = signatureService.createReadySignature(request.orderNo(), request.amount(), timestamp);

        PaymentAuthSession session = PaymentAuthSession.builder()
                .mid(inicisProperties.getMid())
                .orderNo(request.orderNo())
                .amount(request.amount())
                .currency(defaultCurrency(request.currency()))
                .buyerName(request.buyerName().trim())
                .productName(request.productName().trim())
                .authToken(authToken)
                .status(PaymentAuthStatus.REQUEST_READY)
                .expiredAt(LocalDateTime.now().plusMinutes(30))
                .build();
        authSessionRepository.save(session);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("version", "1.0");
        params.put("gopaymethod", "Card");
        params.put("mid", inicisProperties.getMid());
        params.put("oid", request.orderNo());
        params.put("price", request.amount().stripTrailingZeros().toPlainString());
        params.put("timestamp", timestamp);
        params.put("signature", signature);
        params.put("mKey", signatureService.createMKey());
        params.put("returnUrl", inicisProperties.getReturnUrl());
        params.put("closeUrl", inicisProperties.getCloseUrl());

        auditHelper.savePgLog(null, request.orderNo(), PgApiType.STD_PAY_READY, params.toString(), LogResultStatus.READY, "표준결제 요청 파라미터 생성");
        auditHelper.saveAudit("PAYMENT", "READY", request.orderNo(), "Inicis StdPay mock 결제 준비 요청을 생성했습니다.");

        return new InicisReadyResponse(
                inicisProperties.getMid(),
                request.orderNo(),
                request.amount(),
                defaultCurrency(request.currency()),
                authToken,
                signature,
                signatureService.createMKey(),
                inicisProperties.getReturnUrl(),
                inicisProperties.getCloseUrl(),
                params
        );
    }

    @Transactional
    public InicisApproveResponse handleStdPayReturn(InicisAuthResultRequest request) {
        approvalValidator.validateAuthResultRequest(request);
        PaymentAuthSession session = authSessionRepository.findByAuthToken(request.authToken())
                .orElseThrow(() -> new com.yeni.backoffice.core.common.exception.NotFoundException(ErrorCode.PAYMENT_NOT_FOUND, "결제 인증 세션을 찾을 수 없습니다."));
        session.markAuthResultReceived();
        auditHelper.savePgLog(null, request.orderNo(), PgApiType.AUTH_RESULT, request.toString(), LogResultStatus.RECEIVED, request.resultMessage());

        if (session.isExpired(LocalDateTime.now())) {
            session.markFailed();
            throw new ValidationBusinessException(ErrorCode.INVALID_REQUEST, "결제 인증 세션이 만료되었습니다.");
        }
        if (!"0000".equals(request.resultCode())) {
            session.markFailed();
            throw new BusinessException(ErrorCode.PAYMENT_APPROVE_FAILED, "PG 인증 결과가 실패입니다: " + request.resultMessage());
        }
        approvalValidator.validateAuthMatchesSession(request, session);

        PaymentGatewayAdapter adapter = adapterResolver.resolve(PG_COMPANY);
        PaymentGatewayAdapter.ApprovalResult approvalResult = adapter.approve(
                new PaymentGatewayAdapter.ApprovalRequest(
                        request.mid(),
                        request.orderNo(),
                        request.amount(),
                        request.authToken()
                )
        );
        var approvalLog = auditHelper.savePgLog(null, request.orderNo(), PgApiType.APPROVE, request.toString(), LogResultStatus.REQUESTED, "승인 mock 요청");

        if (!approvalResult.success()) {
            approvalLog.complete(approvalResult.toString(), LogResultStatus.FAILED, approvalResult.resultMessage());
            session.markFailed();
            auditHelper.saveAudit("PAYMENT", "APPROVE_FAILED", request.orderNo(), approvalResult.resultMessage());
            throw new BusinessException(ErrorCode.PAYMENT_APPROVE_FAILED, "PG 승인에 실패했습니다: " + approvalResult.resultMessage());
        }

        try {
            approvalValidator.validateApprovalResult(session, approvalResult);
            PaymentTransaction payment = PaymentTransaction.builder()
                    .storeId(resolveStoreId(session.getOrderNo(), null))
                    .mid(session.getMid())
                    .orderNo(session.getOrderNo())
                    .productName(session.getProductName())
                    .tid(approvalResult.tid())
                    .approvalRequestKey("STD_PAY-" + session.getOrderNo())
                    .approvedAmount(session.getAmount())
                    .canceledAmount(BigDecimal.ZERO)
                    .currency(session.getCurrency())
                    .paymentMethod(PaymentDefaults.PAYMENT_METHOD_CARD)
                    .paymentStatus(PaymentStatus.APPROVED)
                    .approvedAt(approvalResult.approvedAt())
                    .build();
            paymentRepository.save(payment);
            session.markApproved(approvalResult.tid());

            SalesTransaction sales = salesLedgerService.createSales(payment, SaleType.SALE, payment.getId(), payment.getApprovedAmount(), approvalResult.approvedAt());
            notificationService.createExternalSendRequest(sales, "SALE-" + payment.getOrderNo());
            notificationService.createAlimtalkQueue(payment, sales, "SALE-" + payment.getOrderNo(), "APPROVE");

            approvalLog.complete(approvalResult.toString(), LogResultStatus.SUCCESS, approvalResult.resultMessage());
            auditHelper.saveAudit("PAYMENT", "APPROVED", payment.getOrderNo(), "PG 승인 완료 후 매출 및 외부 전송 요청을 생성했습니다.");

            return new InicisApproveResponse(
                    payment.getId(),
                    payment.getOrderNo(),
                    payment.getTid(),
                    payment.getPaymentStatus().name(),
                    payment.getApprovedAmount(),
                    payment.getApprovedAt()
            );
        } catch (RuntimeException e) {
            adapter.netCancel(new PaymentGatewayAdapter.NetCancelRequest(approvalResult.tid(), e.getMessage()));
            approvalLog.complete(approvalResult.toString(), LogResultStatus.NET_CANCEL_REQUESTED, e.getMessage());
            auditHelper.saveAudit("PAYMENT", "NET_CANCEL_REQUESTED", request.orderNo(), "승인 후 내부 처리 실패로 netCancel 보상 흐름을 수행했습니다.");
            throw e;
        }
    }

    private PaymentTransaction saveUnknownPayment(PaymentApproveCommand command, PaymentApproveResult result) {
        PaymentTransaction payment = PaymentTransaction.builder()
                .storeId(command.storeId())
                .mid(command.mid())
                .pgProvider(command.pgProvider())
                .orderNo(command.orderNo())
                .productName(command.productName())
                .tid(result.tid() == null ? "UNKNOWN-" + UUID.randomUUID() : result.tid())
                .approvalRequestKey(command.idempotencyKey())
                .approvedAmount(command.amount())
                .canceledAmount(BigDecimal.ZERO)
                .currency(command.currency())
                .paymentMethod(command.paymentMethod())
                .paymentStatus(PaymentStatus.APPROVE_UNKNOWN)
                .approvedAt(LocalDateTime.now())
                .failureReason(result.resultMessage())
                .build();
        return paymentRepository.save(payment);
    }

    private Long resolveStoreId(String orderNo, Long requestedStoreId) {
        if (requestedStoreId != null) return requestedStoreId;
        if (!StringUtils.hasText(orderNo)) return null;
        return orderRepository.findByOrderNo(orderNo).map(order -> order.getStoreId()).orElse(null);
    }

    private PaymentApproveResponse toApproveResponse(PaymentTransaction payment, PgProvider provider, String resultCode, String resultMessage) {
        return new PaymentApproveResponse(
                payment.getId(),
                provider,
                payment.getOrderNo(),
                payment.getTid(),
                payment.getPaymentStatus().name(),
                payment.getApprovedAmount(),
                payment.getApprovedAt(),
                resultCode,
                resultMessage
        );
    }

    private String midByProvider(PgProvider provider) {
        return PgProvider.INICIS.equals(provider) ? inicisProperties.getMid() : "MOCK_MID";
    }

    private String defaultCurrency(String currency) {
        return StringUtils.hasText(currency) ? currency.trim() : "KRW";
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }
}
