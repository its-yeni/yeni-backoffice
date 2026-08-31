package com.yeni.backoffice.core.commerce.entity;

import com.yeni.backoffice.core.commerce.enums.OrderPaymentStatus;
import com.yeni.backoffice.core.commerce.enums.OrderStatus;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "commerce_order",
        uniqueConstraints = @UniqueConstraint(name = "uk_commerce_order_no", columnNames = "orderNo")
)
public class CommerceOrder extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long storeId;

    @Column(length = 40)
    private String storeCode;

    @Column(length = 100)
    private String storeName;

    @Column(nullable = false, length = 100)
    private String orderNo;

    @Column(nullable = false, length = 80)
    private String buyerName;

    @Column(length = 40)
    private String buyerPhone;

    @Column(nullable = false, length = 160)
    private String productName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal productAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal deliveryFee;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal discountAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal payableAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal paidAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal cancelledAmount;

    @Column(nullable = false)
    private boolean stockRestored;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus orderStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderPaymentStatus paymentStatus;

    private Long paymentId;

    @Column(length = 120)
    private String tid;

    @Column(length = 200)
    private String lastMessage;

    private java.time.LocalDateTime purchaseConfirmedAt;

    public void recalculateAmounts(
            BigDecimal productAmount,
            BigDecimal deliveryFee,
            BigDecimal discountAmount) {
        validateNonNegative(productAmount, "상품합계는 0 이상이어야 합니다.");
        validateNonNegative(deliveryFee, "배송비는 0 이상이어야 합니다.");
        validateNonNegative(discountAmount, "할인금액은 0 이상이어야 합니다.");

        BigDecimal calculatedPayableAmount = productAmount.add(deliveryFee).subtract(discountAmount);
        if (calculatedPayableAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "최종 결제금액은 0보다 커야 합니다.");
        }
        if (discountAmount.compareTo(productAmount.add(deliveryFee)) > 0) {
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "할인금액은 상품합계와 배송비 합계를 초과할 수 없습니다.");
        }

        this.productAmount = productAmount;
        this.deliveryFee = deliveryFee;
        this.discountAmount = discountAmount;
        this.payableAmount = calculatedPayableAmount;
    }

    public void markPaymentResult(Long paymentId, String tid, OrderPaymentStatus paymentStatus, OrderStatus orderStatus, String message) {
        this.paymentId = paymentId;
        this.tid = tid;
        this.paymentStatus = paymentStatus;
        this.orderStatus = orderStatus;
        this.lastMessage = message;
    }

    public void markApproved(Long paymentId, String tid, String message) {
        this.paymentId = paymentId;
        this.tid = tid;
        this.paidAmount = payableAmount;
        this.paymentStatus = OrderPaymentStatus.APPROVED;
        this.orderStatus = OrderStatus.PAID;
        this.lastMessage = message;
    }

    public void markApproveUnknown(Long paymentId, String tid, String message) {
        this.paymentId = paymentId;
        this.tid = tid;
        this.paymentStatus = OrderPaymentStatus.APPROVE_UNKNOWN;
        this.lastMessage = message;
    }

    public void markPaymentFailed(String message) {
        this.paymentStatus = OrderPaymentStatus.FAILED;
        this.orderStatus = OrderStatus.PAYMENT_FAILED;
        this.lastMessage = message;
    }

    public void syncCancelledAmount(BigDecimal totalCancelledAmount) {
        validateNonNegative(totalCancelledAmount, "취소 금액은 0 이상이어야 합니다.");
        if (totalCancelledAmount.compareTo(payableAmount) > 0) {
            throw new ValidationBusinessException(ErrorCode.ORDER_PAYMENT_AMOUNT_MISMATCH);
        }
        this.cancelledAmount = totalCancelledAmount;
        this.orderStatus = totalCancelledAmount.compareTo(payableAmount) >= 0
                ? OrderStatus.CANCELLED : OrderStatus.PARTIALLY_CANCELLED;
        this.lastMessage = OrderStatus.CANCELLED.equals(orderStatus) ? "결제가 전액 취소되었습니다." : "결제가 부분 취소되었습니다.";
    }

    public void markStockRestored() {
        this.stockRestored = true;
    }

    /**
     * 배송 완료 후 구매 확정 — 매출이 확정되어 정산 대상이 되는 시점.
     * 이미 확정됐으면(멱등) 아무것도 하지 않고, 전액 취소된 주문은 확정 대상이 아니다.
     * 부분 취소된 주문은 남은 금액에 대해 구매 확정이 성립하므로 허용한다.
     */
    public boolean markPurchaseConfirmed(java.time.LocalDateTime confirmedAt) {
        if (OrderStatus.PURCHASE_CONFIRMED.equals(orderStatus)) return false;
        if (orderStatus != OrderStatus.PAID && orderStatus != OrderStatus.PARTIALLY_CANCELLED) return false;
        this.orderStatus = OrderStatus.PURCHASE_CONFIRMED;
        this.purchaseConfirmedAt = confirmedAt;
        this.lastMessage = "배송 완료로 구매가 확정되었습니다.";
        return true;
    }

    public void assignStore(Long storeId,String storeCode,String storeName){this.storeId=storeId;this.storeCode=storeCode;this.storeName=storeName;}

    private void validateNonNegative(BigDecimal amount, String message) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
    }
}
