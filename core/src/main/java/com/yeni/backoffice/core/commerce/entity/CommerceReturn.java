package com.yeni.backoffice.core.commerce.entity;

import com.yeni.backoffice.core.commerce.enums.RefundStatus;
import com.yeni.backoffice.core.commerce.enums.ReturnResponsibility;
import com.yeni.backoffice.core.commerce.enums.ReturnStatus;
import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.ValidationBusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 주문 반품/환불 도메인. 배송(CommerceDelivery)이 "물건이 어떻게 오갔는지"를 기록하는 것과 달리,
 * 이 엔티티는 "환불 처리를 실제로 진행해도 되는지"를 판단하는 상태 기계다.
 * REQUESTED(접수) → INSPECTING(회수/검수 중) → COMPLETED(검수 통과: 재고 복원 + 환불 확정) / REJECTED(검수 반려).
 * 재고 복원과 PG 환불은 COMPLETED 시점에만 일어난다 — 검수 전에 반영하면 손상품이 판매 가능 재고로 잡히거나
 * 실제로는 회수도 안 된 상품 대금이 먼저 나가는 사고로 이어지기 때문이다.
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "commerce_return")
public class CommerceReturn extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    /** 어느 배송 건에 대한 반품인지. 부분배송으로 여러 건이 나간 주문에서 그중 일부만 반품될 수 있으므로 남겨둔다.
     * 배송 분리 전에 접수된 반품이거나 배송지 정보가 없는 주문이면 null일 수 있다. */
    @Column
    private Long deliveryId;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReturnResponsibility responsibility;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal returnShippingFee;

    /** 검수 완료 시점에 확정되는 실제 환불 금액. 그 전까지는 null이다(아직 확정 전이라는 뜻). */
    @Column(precision = 19, scale = 2)
    private BigDecimal refundAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReturnStatus status;

    @Column
    private LocalDateTime inspectedAt;

    /** COMPLETED든 REJECTED든, 이 반품 건이 최종 처리된 시각. */
    @Column
    private LocalDateTime processedAt;

    @Column(length = 500)
    private String rejectReason;

    /** 실제 PG 취소(환불) 처리 결과. ReturnStatus와 별도로 둔 이유는 클래스 상단 주석 참고. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private RefundStatus refundStatus;

    @Column(length = 500)
    private String refundFailureReason;

    @Builder
    public CommerceReturn(Long orderId, Long deliveryId, String reason, ReturnResponsibility responsibility, BigDecimal returnShippingFee) {
        if (orderId == null) throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "주문 정보가 없습니다.");
        if (reason == null || reason.isBlank()) throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "반품 사유를 입력해 주세요.");
        if (responsibility == null) throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "반품 귀책을 선택해 주세요.");
        this.orderId = orderId;
        this.deliveryId = deliveryId;
        this.reason = reason;
        this.responsibility = responsibility;
        this.returnShippingFee = returnShippingFee == null ? BigDecimal.ZERO : returnShippingFee;
        this.status = ReturnStatus.REQUESTED;
    }

    public void startInspection() {
        if (status != ReturnStatus.REQUESTED)
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "접수 상태에서만 검수를 시작할 수 있습니다.");
        this.status = ReturnStatus.INSPECTING;
        this.inspectedAt = LocalDateTime.now();
    }

    /** 검수 통과 처리. 반송비를 검수 시점에 재조정할 수 있게 반영값을 다시 받는다(사진과 다른 사유로 접수됐을 수 있어서).
     * 환불할 금액이 있으면 환불 상태를 PENDING으로 걸어둔다 — 실제 PG 취소 결과는 이후
     * {@link #markRefundSucceeded()}/{@link #markRefundFailed} 로 비동기로 반영된다. */
    public void complete(BigDecimal returnShippingFeeOverride, BigDecimal refundAmount) {
        if (status != ReturnStatus.INSPECTING)
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "검수 중 상태에서만 완료 처리할 수 있습니다.");
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) < 0)
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "환불 금액이 올바르지 않습니다.");
        if (returnShippingFeeOverride != null) this.returnShippingFee = returnShippingFeeOverride;
        this.refundAmount = refundAmount;
        this.status = ReturnStatus.COMPLETED;
        this.processedAt = LocalDateTime.now();
        this.refundStatus = refundAmount.compareTo(BigDecimal.ZERO) > 0 ? RefundStatus.PENDING : RefundStatus.NOT_REQUIRED;
    }

    public void markRefundSucceeded() {
        this.refundStatus = RefundStatus.SUCCESS;
        this.refundFailureReason = null;
    }

    public void markRefundFailed(String reason) {
        this.refundStatus = RefundStatus.FAILED;
        this.refundFailureReason = reason;
    }

    /** 환불 실패 건을 다시 시도하기 직전에 부른다. 실패 상태에서만 재시도할 수 있다 — 이미 성공했거나
     * 애초에 환불이 필요 없던 건을 다시 이벤트 발행 대상으로 만들면 중복 취소 시도가 된다. */
    public void markRefundPending() {
        if (refundStatus != RefundStatus.FAILED)
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "환불 실패 상태인 반품만 재시도할 수 있습니다.");
        this.refundStatus = RefundStatus.PENDING;
        this.refundFailureReason = null;
    }

    public void reject(String rejectReason) {
        if (status != ReturnStatus.INSPECTING)
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "검수 중 상태에서만 반려 처리할 수 있습니다.");
        if (rejectReason == null || rejectReason.isBlank())
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "반려 사유를 입력해 주세요.");
        this.rejectReason = rejectReason;
        this.status = ReturnStatus.REJECTED;
        this.processedAt = LocalDateTime.now();
    }
}
