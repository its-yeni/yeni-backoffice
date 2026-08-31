package com.yeni.backoffice.core.payment.dto;

import com.yeni.backoffice.core.payment.entity.PaymentTransaction;
import com.yeni.backoffice.core.payment.enums.PgProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class PaymentBridgeDtos {

    private PaymentBridgeDtos() {
    }

    @Schema(description = "PGB 공통 결제 승인 요청")
    public record PaymentApproveRequest(
            @Schema(description = "사용할 PG Provider. 없으면 MOCK으로 라우팅합니다.", example = "MOCK")
            PgProvider pgProvider,
            @Schema(description = "주문번호", example = "ORDER-20260603-001")
            @NotBlank(message = "주문번호는 필수입니다.")
            String orderNo,
            @Schema(description = "승인금액", example = "12000")
            @NotNull(message = "승인금액은 필수입니다.")
            @Positive(message = "승인금액은 0보다 커야 합니다.")
            BigDecimal amount,
            @Schema(description = "통화 코드", example = "KRW")
            String currency,
            @Schema(description = "구매자명", example = "Portfolio Buyer")
            String buyerName,
            @Schema(description = "상품명", example = "PG Bridge Mock Item")
            String productName,
            @Schema(description = "중복 승인 방지 키. 없으면 서버에서 생성합니다.")
            String idempotencyKey,
            @Schema(description = "채널 유형. 향후 PG 라우팅 확장 지점입니다.", example = "WEB")
            String channelType,
            @Schema(description = "매장/사이트 코드. 향후 PG 라우팅 확장 지점입니다.", example = "PORTFOLIO")
            String storeCode,
            @Schema(description = "결제수단", example = "CARD")
            String paymentMethod,
            Long storeId
    ) {
        public PaymentApproveRequest(PgProvider pgProvider,String orderNo,BigDecimal amount,String currency,String buyerName,String productName,String idempotencyKey,String channelType,String storeCode,String paymentMethod){this(pgProvider,orderNo,amount,currency,buyerName,productName,idempotencyKey,channelType,storeCode,paymentMethod,null);}
    }

    @Schema(description = "PGB 공통 결제 승인 응답")
    public record PaymentApproveResponse(
            Long paymentId,
            PgProvider pgProvider,
            String orderNo,
            String tid,
            String paymentStatus,
            BigDecimal approvedAmount,
            LocalDateTime approvedAt,
            String resultCode,
            String resultMessage
    ) {
    }

    @Schema(description = "PGB 공통 결제 취소 요청")
    public record PaymentBridgeCancelRequest(
            @Schema(description = "사용할 PG Provider. 없으면 결제 승인 Provider를 사용합니다.", example = "MOCK")
            PgProvider pgProvider,
            @Schema(description = "취소금액", example = "3000")
            @NotNull(message = "취소금액은 필수입니다.")
            @Positive(message = "취소금액은 0보다 커야 합니다.")
            BigDecimal cancelAmount,
            @Schema(description = "취소 사유", example = "portfolio partial cancel")
            String cancelReason,
            @Schema(description = "중복 취소 방지 키")
            @NotBlank(message = "취소 중복 방지 키는 필수입니다.")
            String idempotencyKey
    ) {
    }

    @Schema(description = "PGB 공통 결제 취소 응답")
    public record PaymentBridgeCancelResponse(
            Long cancelId,
            Long paymentId,
            PgProvider pgProvider,
            String tid,
            BigDecimal cancelAmount,
            String cancelType,
            String paymentStatus,
            String resultCode,
            String resultMessage
    ) {
    }

    @Schema(description = "PGB PG 거래 조회 응답")
    public record PaymentQueryResponse(
            Long paymentId,
            PgProvider pgProvider,
            String orderNo,
            String tid,
            String paymentStatus,
            String resultCode,
            String resultMessage
    ) {
        public static PaymentQueryResponse from(PaymentTransaction payment, PgProvider provider, String resultCode, String resultMessage) {
            return new PaymentQueryResponse(
                    payment.getId(),
                    provider,
                    payment.getOrderNo(),
                    payment.getTid(),
                    payment.getPaymentStatus().name(),
                    resultCode,
                    resultMessage
            );
        }
    }
}
