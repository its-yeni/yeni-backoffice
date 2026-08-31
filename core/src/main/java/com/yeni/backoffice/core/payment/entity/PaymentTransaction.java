package com.yeni.backoffice.core.payment.entity;

import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import com.yeni.backoffice.core.payment.enums.PaymentStatus;
import com.yeni.backoffice.core.payment.enums.PgProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "payment_transaction",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_transaction_order_no", columnNames = "orderNo"),
                @UniqueConstraint(name = "uk_payment_transaction_tid", columnNames = "tid"),
                @UniqueConstraint(name = "uk_payment_transaction_approval_key", columnNames = "approvalRequestKey")
        }
)
public class PaymentTransaction extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long storeId;

    @Column(nullable = false, length = 40)
    private String mid;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PgProvider pgProvider = PgProvider.MOCK;

    @Column(nullable = false, length = 80)
    private String orderNo;

    @Column(length = 200)
    private String productName;

    @Column(nullable = false, length = 120)
    private String tid;

    @Column(length = 120)
    private String approvalRequestKey;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal approvedAmount;

    @Builder.Default
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal canceledAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 10)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus paymentStatus;

    @Column(nullable = false)
    private LocalDateTime approvedAt;

    @Column(length = 200)
    private String failureReason;

    @Version
    private Long version;

    public void cancel(BigDecimal amount) {
        this.canceledAmount = this.canceledAmount.add(amount);
        if (this.canceledAmount.compareTo(this.approvedAmount) >= 0) {
            this.paymentStatus = PaymentStatus.CANCELED;
        } else {
            this.paymentStatus = PaymentStatus.PARTIAL_CANCELED;
        }
    }

    public void updateStatus(PaymentStatus paymentStatus, String failureReason) {
        this.paymentStatus = paymentStatus;
        this.failureReason = failureReason;
    }

    public BigDecimal getCancelableAmount() {
        return approvedAmount.subtract(canceledAmount);
    }

    public boolean isCancelCompleted() {
        return PaymentStatus.CANCELED.equals(paymentStatus);
    }

    public void assignStore(Long storeId) { this.storeId = storeId; }
}
