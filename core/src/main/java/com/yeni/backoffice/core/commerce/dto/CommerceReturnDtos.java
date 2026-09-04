package com.yeni.backoffice.core.commerce.dto;

import com.yeni.backoffice.core.commerce.entity.CommerceReturn;
import com.yeni.backoffice.core.commerce.enums.ReturnResponsibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class CommerceReturnDtos {
    private CommerceReturnDtos() {}

    /** 반품 접수. deliveryId는 부분배송 중 어느 박스에 대한 반품인지 특정할 때만 채운다(없어도 접수는 된다). */
    public record ReturnCreateRequest(
            @NotNull(message = "주문 정보가 없습니다.") Long orderId,
            Long deliveryId,
            @NotBlank(message = "반품 사유를 입력해 주세요.") String reason,
            @NotNull(message = "반품 귀책을 선택해 주세요.") ReturnResponsibility responsibility,
            BigDecimal returnShippingFee) {}

    /** 검수 완료 처리. 접수 시 입력한 반송비를 실물 검수 결과에 맞춰 다시 조정할 수 있게 override를 받는다. */
    public record CompleteRequest(BigDecimal returnShippingFee) {}

    public record RejectRequest(@NotBlank(message = "반려 사유를 입력해 주세요.") String reason) {}

    public record ReturnResponse(Long id, Long orderId, String orderNo, String buyerName, Long deliveryId,
            String reason, String responsibility, BigDecimal returnShippingFee, BigDecimal refundAmount,
            String status, LocalDateTime requestedAt, LocalDateTime inspectedAt, LocalDateTime processedAt,
            String rejectReason, String refundStatus, String refundFailureReason, Long storeId) {
        public static ReturnResponse from(CommerceReturn r, String orderNo, String buyerName, Long storeId) {
            return new ReturnResponse(r.getId(), r.getOrderId(), orderNo, buyerName, r.getDeliveryId(),
                    r.getReason(), r.getResponsibility().name(), r.getReturnShippingFee(), r.getRefundAmount(),
                    r.getStatus().name(), r.getCreatedAt(), r.getInspectedAt(), r.getProcessedAt(), r.getRejectReason(),
                    r.getRefundStatus() == null ? null : r.getRefundStatus().name(), r.getRefundFailureReason(), storeId);
        }
    }
}
