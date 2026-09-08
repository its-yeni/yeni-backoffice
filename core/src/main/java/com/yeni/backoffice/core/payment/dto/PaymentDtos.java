package com.yeni.backoffice.core.payment.dto;

import com.yeni.backoffice.core.payment.entity.AlimtalkQueue;
import com.yeni.backoffice.core.payment.entity.ExternalSendRequest;
import com.yeni.backoffice.core.payment.entity.PaymentRecoveryTask;
import com.yeni.backoffice.core.payment.entity.PaymentTransaction;
import com.yeni.backoffice.core.payment.entity.PgApiLog;
import com.yeni.backoffice.core.payment.entity.PgFeePolicy;
import com.yeni.backoffice.core.payment.entity.SalesTransaction;
import com.yeni.backoffice.core.payment.entity.SettlementDetail;
import com.yeni.backoffice.core.payment.entity.SettlementFeeDetail;
import com.yeni.backoffice.core.payment.entity.SettlementLog;
import com.yeni.backoffice.core.payment.entity.SettlementStatement;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    @Schema(description = "Inicis StdPay 결제 준비 요청")
    public record InicisReadyRequest(
            @Schema(description = "주문번호", example = "ORDER-20260603-001")
            @NotBlank(message = "주문번호는 필수입니다.")
            String orderNo,
            @Schema(description = "결제 요청 금액", example = "12000")
            @NotNull(message = "결제금액은 필수입니다.")
            @Positive(message = "결제금액은 0보다 커야 합니다.")
            BigDecimal amount,
            @Schema(description = "통화 코드", example = "KRW")
            String currency,
            @Schema(description = "구매자명", example = "Portfolio Buyer")
            @NotBlank(message = "구매자명은 필수입니다.")
            String buyerName,
            @Schema(description = "상품명", example = "PG Adapter Mock Item")
            @NotBlank(message = "상품명은 필수입니다.")
            String productName
    ) {
    }

    @Schema(description = "Inicis StdPay 결제 준비 응답")
    public record InicisReadyResponse(
            @Schema(description = "Mock 가맹점 ID") String mid,
            @Schema(description = "주문번호") String orderNo,
            @Schema(description = "결제 요청 금액") BigDecimal amount,
            @Schema(description = "통화 코드") String currency,
            @Schema(description = "Mock 인증 토큰") String authToken,
            @Schema(description = "결제 요청 서명값") String signature,
            @Schema(description = "signKey 해시값. 실제 운영 키는 저장하지 않습니다.") String mKey,
            @Schema(description = "결제 결과 수신 URL") String returnUrl,
            @Schema(description = "결제창 닫기 URL") String closeUrl,
            @Schema(description = "표준결제 요청 파라미터") Map<String, String> stdPayParams
    ) {
    }

    @Schema(description = "Inicis StdPay 인증 결과 수신 요청")
    public record InicisAuthResultRequest(
            @Schema(description = "PG 가맹점 ID", example = "INIpayTest") String mid,
            @Schema(description = "주문번호", example = "ORDER-20260603-001") String orderNo,
            @Schema(description = "결제 준비 단계에서 발급한 Mock 인증 토큰") String authToken,
            @Schema(description = "인증 금액", example = "12000") BigDecimal amount,
            @Schema(description = "PG 인증 결과 코드. 0000이면 성공", example = "0000") String resultCode,
            @Schema(description = "PG 인증 결과 메시지", example = "mock auth success") String resultMessage,
            @Schema(description = "주문번호, 금액, 인증 토큰 기반 검증 서명") String signature
    ) {
    }

    @Schema(description = "결제 승인 처리 응답")
    public record InicisApproveResponse(
            @Schema(description = "결제 거래 ID") Long paymentId,
            @Schema(description = "주문번호") String orderNo,
            @Schema(description = "PG 거래번호") String tid,
            @Schema(description = "결제 상태", example = "APPROVED") String paymentStatus,
            @Schema(description = "승인 금액") BigDecimal approvedAmount,
            @Schema(description = "승인 시각") LocalDateTime approvedAt
    ) {
    }

    @Schema(description = "결제 취소 요청")
    public record PaymentCancelRequest(
            @Schema(description = "취소 금액. 승인 잔여 금액을 초과할 수 없습니다.", example = "3000")
            @NotNull(message = "취소금액은 필수입니다.")
            @Positive(message = "취소금액은 0보다 커야 합니다.")
            BigDecimal cancelAmount,
            @Schema(description = "취소 사유", example = "portfolio partial cancel")
            String cancelReason,
            @Schema(description = "취소 중복 처리 방지 키", example = "CANCEL-20260603-001")
            @NotBlank(message = "취소 중복 방지 키는 필수입니다.")
            String cancelRequestKey
    ) {
    }

    @Schema(description = "결제 취소 응답")
    public record PaymentCancelResponse(
            @Schema(description = "취소 ID") Long cancelId,
            @Schema(description = "결제 거래 ID") Long paymentId,
            @Schema(description = "PG 거래번호") String tid,
            @Schema(description = "취소 금액") BigDecimal cancelAmount,
            @Schema(description = "취소 유형", example = "PARTIAL") String cancelType,
            @Schema(description = "취소 후 결제 상태", example = "PARTIAL_CANCELED") String paymentStatus
    ) {
    }

    @Schema(description = "결제 거래 조회 응답")
    public record PaymentResponse(
            @Schema(description = "결제 거래 ID") Long id,
            @Schema(description = "PG 가맹점 ID") String mid,
            @Schema(description = "주문번호") String orderNo,
            @Schema(description = "대표 상품명") String productName,
            @Schema(description = "PG 거래번호") String tid,
            @Schema(description = "승인 금액") BigDecimal approvedAmount,
            @Schema(description = "누적 취소 금액") BigDecimal canceledAmount,
            @Schema(description = "통화 코드") String currency,
            @Schema(description = "결제 상태") String paymentStatus,
            @Schema(description = "승인 시각") LocalDateTime approvedAt,
            Long storeId,
            @Schema(description = "결제수단 CARD/CASH 등") String paymentMethod,
            @Schema(description = "결제 채널 WEB/POS") String channelType,
            @Schema(description = "카드사 승인번호") String approvalNo,
            @Schema(description = "발급 카드사") String issuerName,
            @Schema(description = "카드번호 뒤 4자리") String cardLast4,
            @Schema(description = "할부 개월 (0=일시불)") Integer installmentMonths,
            @Schema(description = "매입 상태 APPROVED/ACQUIRED/UNSETTLED/N/A") String acquiringStatus,
            @Schema(description = "정산 예정일") java.time.LocalDate settlementDueDate
    ) {
        public static PaymentResponse from(PaymentTransaction payment) {
            return new PaymentResponse(
                    payment.getId(),
                    payment.getMid(),
                    payment.getOrderNo(),
                    payment.getProductName(),
                    payment.getTid(),
                    payment.getApprovedAmount(),
                    payment.getCanceledAmount(),
                    payment.getCurrency(),
                    payment.getPaymentStatus().name(),
                    payment.getApprovedAt(),
                    payment.getStoreId(),
                    payment.getPaymentMethod(),
                    payment.getChannelType() == null ? "WEB" : payment.getChannelType(),
                    payment.getApprovalNo(),
                    payment.getIssuerName(),
                    payment.getCardLast4(),
                    payment.getInstallmentMonths(),
                    payment.getAcquiringStatus(),
                    payment.getSettlementDueDate()
            );
        }
    }

    public record PaymentCancelTraceResponse(
            Long id,
            Long paymentId,
            String tid,
            BigDecimal cancelAmount,
            String cancelType,
            String cancelStatus,
            String cancelReason,
            LocalDateTime canceledAt
    ) {
        public static PaymentCancelTraceResponse from(com.yeni.backoffice.core.payment.entity.PaymentCancel cancel) {
            return new PaymentCancelTraceResponse(
                    cancel.getId(),
                    cancel.getPaymentId(),
                    cancel.getTid(),
                    cancel.getCancelAmount(),
                    cancel.getCancelType().name(),
                    cancel.getCancelStatus().name(),
                    cancel.getCancelReason(),
                    cancel.getCanceledAt()
            );
        }
    }

    public record PaymentTraceResponse(
            PaymentResponse payment,
            List<PaymentCancelTraceResponse> cancels,
            List<SalesResponse> sales,
            List<ExternalSendResponse> externalSends,
            List<AlimtalkQueueResponse> alimtalkQueues,
            List<RecoveryTaskResponse> recoveryTasks,
            List<SettlementDetailResponse> settlementDetails,
            List<PgLogResponse> pgLogs
    ) {
    }

    @Schema(description = "PG API 로그 조회 응답")
    public record PgLogResponse(
            Long id,
            String requestId,
            Long paymentId,
            String orderNo,
            String pgCompany,
            String apiType,
            String requestBody,
            String responseBody,
            String resultStatus,
            String resultMessage,
            LocalDateTime loggedAt
    ) {
        public static PgLogResponse from(PgApiLog log) {
            return new PgLogResponse(
                    log.getId(),
                    log.getRequestId(),
                    log.getPaymentId(),
                    log.getOrderNo(),
                    log.getPgCompany().name(),
                    log.getApiType().name(),
                    log.getRequestBody(),
                    log.getResponseBody(),
                    log.getResultStatus().name(),
                    log.getResultMessage(),
                    log.getLoggedAt()
            );
        }
    }

    @Schema(description = "매출 원장 거래 조회 응답")
    public record SalesResponse(
            Long id,
            Long paymentId,
            Long cancelId,
            Long originalSalesTransactionId,
            String orderNo,
            String tid,
            String pgTransactionId,
            String saleType,
            BigDecimal supplyAmount,
            BigDecimal vatAmount,
            BigDecimal totalAmount,
            BigDecimal saleAmount,
            String saleStatus,
            String ledgerStatus,
            String settlementStatus,
            LocalDate businessDate,
            LocalDateTime occurredAt,
            Boolean settlementIncludedYn,
            String pgCode,
            String paymentMethod,
            Long sellerId,
            Long orderItemId,
            Long storeId,
            Boolean confirmedYn,
            LocalDateTime confirmedAt
    ) {
        public static SalesResponse from(SalesTransaction sales) {
            return new SalesResponse(
                    sales.getId(),
                    sales.getPaymentId(),
                    sales.getCancelId(),
                    sales.getOriginalSalesTransactionId(),
                    sales.getOrderNo(),
                    sales.getTid(),
                    sales.getPgTransactionId(),
                    sales.getSaleType().name(),
                    sales.getSupplyAmount(),
                    sales.getVatAmount(),
                    sales.getTotalAmount(),
                    sales.getSaleAmount(),
                    sales.getSaleStatus().name(),
                    sales.getLedgerStatus().name(),
                    sales.getSettlementStatus().name(),
                    sales.getBusinessDate(),
                    sales.getOccurredAt(),
                    sales.getSettlementIncludedYn(),
                    sales.getPgCode(),
                    sales.getPaymentMethod(),
                    sales.getSellerId(),
                    sales.getOrderItemId(),
                    sales.getStoreId(),
                    sales.getConfirmedYn(),
                    sales.getConfirmedAt()
            );
        }
    }

    @Schema(description = "매출 원장 요약 응답")
    public record SalesLedgerSummaryResponse(
            BigDecimal totalSaleAmount,
            BigDecimal totalCancelAmount,
            BigDecimal netSalesAmount,
            long notSettledCount,
            long calculatedCount,
            long settledCount,
            long paidCount,
            long carriedOverCount,
            long excludedCount,
            long confirmedCount,
            long unconfirmedCount
    ) {
    }

    public record SalesLedgerPageResponse(
            List<SalesResponse> data,
            long totalCount,
            int page,
            int size,
            SalesLedgerSummaryResponse summary
    ) {
    }

    public record SalesLedgerLinksResponse(
            Long salesTransactionId,
            SalesResponse originalSale,
            PaymentResponse payment,
            Long cancelId,
            BigDecimal cumulativeCanceledAmount,
            BigDecimal cancelableAmount,
            String cancelReason,
            LocalDateTime canceledAt,
            List<ExternalSendResponse> externalSends,
            List<AlimtalkQueueResponse> alimtalkQueues,
            List<RecoveryTaskResponse> recoveryTasks,
            List<SettlementDetailResponse> settlementDetails
    ) {
    }

    public record SalesAdjustmentRequest(
            @Schema(description = "조정 유형", example = "MANUAL_ADJUST")
            @NotBlank(message = "조정 유형은 필수입니다.")
            String adjustmentType,
            @Schema(description = "조정 금액", example = "1000")
            @NotNull(message = "조정금액은 필수입니다.")
            BigDecimal adjustmentAmount,
            @Schema(description = "조정 사유")
            String reason
    ) {
    }

    @Schema(description = "외부전송 요청 조회 응답")
    public record ExternalSendResponse(
            Long id,
            Long salesId,
            String requestKey,
            String targetSystem,
            String sendStatus,
            Integer retryCount,
            Integer maxRetryCount,
            String lastErrorMessage,
            LocalDateTime processingStartedAt,
            LocalDateTime lastSentAt
    ) {
        public static ExternalSendResponse from(ExternalSendRequest request) {
            return new ExternalSendResponse(
                    request.getId(),
                    request.getSalesId(),
                    request.getRequestKey(),
                    request.getTargetSystem(),
                    request.getSendStatus().name(),
                    request.getRetryCount(),
                    request.getMaxRetryCount(),
                    request.getLastErrorMessage(),
                    request.getProcessingStartedAt(),
                    request.getLastSentAt()
            );
        }
    }

    @Schema(description = "알림톡 Queue 조회 응답")
    public record AlimtalkQueueResponse(
            Long id,
            Long paymentId,
            Long salesId,
            String messageKey,
            String eventType,
            String status,
            Integer retryCount,
            Integer maxRetryCount,
            String lastErrorMessage,
            LocalDateTime processingStartedAt,
            LocalDateTime sentAt
    ) {
        public static AlimtalkQueueResponse from(AlimtalkQueue queue) {
            return new AlimtalkQueueResponse(
                    queue.getId(),
                    queue.getPaymentId(),
                    queue.getSalesId(),
                    queue.getMessageKey(),
                    queue.getEventType(),
                    queue.getStatus().name(),
                    queue.getRetryCount(),
                    queue.getMaxRetryCount(),
                    queue.getLastErrorMessage(),
                    queue.getProcessingStartedAt(),
                    queue.getSentAt()
            );
        }
    }

    @Schema(description = "RecoveryTask 조회 응답")
    public record RecoveryTaskResponse(
            Long id,
            String taskKey,
            Long paymentId,
            Long cancelId,
            String orderNo,
            String tid,
            String idempotencyKey,
            String recoveryType,
            String status,
            Integer retryCount,
            Integer maxRetryCount,
            String lastErrorMessage,
            LocalDateTime lastTriedAt,
            LocalDateTime processedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static RecoveryTaskResponse from(PaymentRecoveryTask task) {
            return new RecoveryTaskResponse(
                    task.getId(),
                    task.getTaskKey(),
                    task.getPaymentId(),
                    task.getCancelId(),
                    task.getOrderNo(),
                    task.getTid(),
                    task.getIdempotencyKey(),
                    task.getRecoveryType().name(),
                    task.getStatus().name(),
                    task.getRetryCount(),
                    task.getMaxRetryCount(),
                    task.getLastErrorMessage(),
                    task.getLastTriedAt(),
                    task.getProcessedAt(),
                    task.getCreatedAt(),
                    task.getUpdatedAt()
            );
        }
    }

    public record RecoveryTaskPageResponse(
            List<RecoveryTaskResponse> data,
            long totalCount,
            int page,
            int size
    ) {
    }

    @Schema(description = "시나리오 실행 타임라인 단계")
    public record ScenarioTimelineStep(
            String stepName,
            String status,
            String description,
            String referenceId,
            LocalDateTime createdAt
    ) {
    }

    @Schema(description = "시나리오 실행 응답")
    public record ScenarioRunResponse(
            String scenarioName,
            String orderNo,
            Long paymentId,
            String status,
            String message,
            List<ScenarioTimelineStep> timelineSteps
    ) {
    }

    @Schema(description = "PG 수수료 정책 등록 요청")
    public record PgFeePolicyRequest(
            @Schema(description = "PG사", example = "INICIS")
            @NotBlank(message = "PG사는 필수입니다.")
            String pgCompany,
            @Schema(description = "가맹점 ID", example = "INIpayTest")
            @NotBlank(message = "가맹점 ID는 필수입니다.")
            String mid,
            @Schema(description = "결제수단", example = "CARD")
            @NotBlank(message = "결제수단은 필수입니다.")
            String paymentMethod,
            @Schema(description = "수수료율", example = "0.0250")
            @NotNull(message = "수수료율은 필수입니다.")
            BigDecimal feeRate,
            @Schema(description = "적용 시작일")
            @NotNull(message = "적용 시작일은 필수입니다.")
            LocalDate effectiveStartDate,
            @Schema(description = "적용 종료일")
            @NotNull(message = "적용 종료일은 필수입니다.")
            LocalDate effectiveEndDate
    ) {
    }

    @Schema(description = "PG 수수료 정책 조회 응답")
    public record PgFeePolicyResponse(
            Long id,
            String pgCompany,
            String mid,
            String paymentMethod,
            BigDecimal feeRate,
            LocalDate effectiveStartDate,
            LocalDate effectiveEndDate,
            Boolean useYn
    ) {
        public static PgFeePolicyResponse from(PgFeePolicy policy) {
            return new PgFeePolicyResponse(
                    policy.getId(),
                    policy.getPgCompany(),
                    policy.getMid(),
                    policy.getPaymentMethod(),
                    policy.getFeeRate(),
                    policy.getEffectiveStartDate(),
                    policy.getEffectiveEndDate(),
                    policy.getUseYn()
            );
        }
    }

    @Schema(description = "정산 배치 실행 요청")
    public record SettlementBatchRunRequest(
            @Schema(description = "정산 대상 영업일")
            LocalDate targetDate,
            Long storeId
    ) {
        public SettlementBatchRunRequest(LocalDate targetDate) {
            this(targetDate, null);
        }
    }

    @Schema(description = "정산 명세 조회 응답")
    public record SettlementStatementResponse(
            Long id,
            LocalDate settlementDate,
            String pgCompany,
            String mid,
            BigDecimal grossAmount,
            BigDecimal feeAmount,
            BigDecimal vatAmount,
            BigDecimal netAmount,
            String settlementStatus,
            BigDecimal saleAmount,
            BigDecimal cancelAmount,
            Long storeId,
            BigDecimal adjustmentAmount,
            BigDecimal holdAmount,
            LocalDate scheduledPayoutDate,
            LocalDateTime paidAt,
            String payoutReference,
            String payoutAccountMasked
    ) {
        public SettlementStatementResponse(
                Long id, LocalDate settlementDate, String pgCompany, String mid,
                BigDecimal grossAmount, BigDecimal feeAmount, BigDecimal vatAmount,
                BigDecimal netAmount, String settlementStatus,
                BigDecimal saleAmount, BigDecimal cancelAmount) {
            this(id, settlementDate, pgCompany, mid, grossAmount, feeAmount, vatAmount,
                    netAmount, settlementStatus, saleAmount, cancelAmount, null,
                    BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, null);
        }

        public static SettlementStatementResponse from(SettlementStatement statement) {
            return from(statement, List.of());
        }

        public static SettlementStatementResponse from(
                SettlementStatement statement,
                List<SettlementDetail> details) {
            BigDecimal saleAmount = details.stream()
                    .filter(detail -> "SALE".equals(detail.getSaleType()))
                    .map(SettlementDetail::getSaleAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal cancelAmount = details.stream()
                    .filter(detail -> "CANCEL".equals(detail.getSaleType()))
                    .map(SettlementDetail::getSaleAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new SettlementStatementResponse(
                    statement.getId(),
                    statement.getSettlementDate(),
                    statement.getPgCompany(),
                    statement.getMid(),
                    statement.getGrossAmount(),
                    statement.getFeeAmount(),
                    statement.getVatAmount(),
                    statement.getNetAmount(),
                    statement.getSettlementStatus().name(),
                    saleAmount,
                    cancelAmount,
                    statement.getStoreId(),
                    statement.getAdjustmentAmount(),
                    statement.getHoldAmount(),
                    statement.getScheduledPayoutDate(),
                    statement.getPaidAt(),
                    statement.getPayoutReference(),
                    statement.getPayoutAccountMasked()
            );
        }
    }

    @Schema(description = "정산 상세 조회 응답")
    public record SettlementDetailResponse(
            Long id,
            Long settlementStatementId,
            Long salesId,
            String saleType,
            BigDecimal saleAmount,
            BigDecimal feeAmount,
            BigDecimal netAmount
    ) {
        public static SettlementDetailResponse from(SettlementDetail detail) {
            return new SettlementDetailResponse(
                    detail.getId(),
                    detail.getSettlementStatementId(),
                    detail.getSalesId(),
                    detail.getSaleType(),
                    detail.getSaleAmount(),
                    detail.getFeeAmount(),
                    detail.getNetAmount()
            );
        }
    }

    @Schema(description = "정산 수수료 상세 조회 응답")
    public record SettlementFeeDetailResponse(
            Long id,
            Long feePolicyId,
            BigDecimal feeRate,
            BigDecimal feeAmount,
            BigDecimal vatAmount
    ) {
        public static SettlementFeeDetailResponse from(SettlementFeeDetail detail) {
            return new SettlementFeeDetailResponse(
                    detail.getId(),
                    detail.getFeePolicyId(),
                    detail.getFeeRate(),
                    detail.getFeeAmount(),
                    detail.getVatAmount()
            );
        }
    }

    public record SettlementReconciliationResponse(
            BigDecimal ledgerGrossAmount,
            BigDecimal statementGrossAmount,
            BigDecimal grossDifference,
            BigDecimal policyFeeAmount,
            BigDecimal statementFeeAmount,
            BigDecimal feeDifference,
            BigDecimal policyVatAmount,
            BigDecimal statementVatAmount,
            BigDecimal vatDifference,
            BigDecimal calculatedNetAmount,
            BigDecimal statementNetAmount,
            BigDecimal netDifference,
            int transactionCount,
            boolean ledgerMatched,
            boolean feeMatched,
            boolean netMatched,
            boolean confirmable,
            List<String> blockingReasons
    ) {
    }

    public record SettlementAdjustmentRequest(
            BigDecimal adjustmentAmount,
            BigDecimal holdAmount,
            LocalDate scheduledPayoutDate,
            String reason
    ) {
    }

    public record SettlementPayRequest(
            String payoutReference,
            String payoutAccountMasked
    ) {
    }

    public record SettlementLogResponse(
            Long id,
            String actionType,
            String resultStatus,
            String message,
            LocalDateTime loggedAt
    ) {
        public static SettlementLogResponse from(SettlementLog log) {
            return new SettlementLogResponse(log.getId(), log.getActionType(), log.getResultStatus(), log.getMessage(), log.getLoggedAt());
        }
    }

    public record SettlementDetailPageResponse(
            SettlementStatementResponse statement,
            List<SettlementDetailResponse> details,
            List<SettlementFeeDetailResponse> feeDetails,
            SettlementReconciliationResponse reconciliation,
            List<SettlementLogResponse> logs,
            List<com.yeni.backoffice.core.payment.dto.SalesAnalyticsDtos.SettlementCategoryRow> categoryBreakdown
    ) {
    }

    @Schema(description = "PG 운영 요약 통계 응답")
    public record OperationSummaryResponse(
            LocalDate startDate,
            LocalDate endDate,
            long approvedPaymentCount,
            BigDecimal approvedAmount,
            long salesTransactionCount,
            BigDecimal salesAmount,
            long readyExternalSendCount,
            long failedExternalSendCount,
            long recoveryTaskCount,
            long readyAlimtalkCount,
            long failedAlimtalkCount
    ) {
    }
}
