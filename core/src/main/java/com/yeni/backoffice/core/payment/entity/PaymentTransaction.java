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
import java.time.LocalDate;
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

    @Builder.Default
    @Column(nullable = false, length = 20)
    private String paymentMethod = "CARD";

    /** 결제 채널 — WEB(온라인 PG) / POS(매장 단말·VAN). */
    @Builder.Default
    @Column(length = 10)
    private String channelType = "WEB";

    /** 카드사 승인번호 (카드사 발급, 8자리). */
    @Column(length = 20)
    private String approvalNo;

    /** 발급 카드사 (신한/삼성/현대/국민/롯데/BC/우리/하나 등). */
    @Column(length = 20)
    private String issuerName;

    /** 카드번호 마스킹 뒤 4자리. */
    @Column(length = 4)
    private String cardLast4;

    /** 할부 개월 (0 = 일시불). */
    @Builder.Default
    @Column
    private Integer installmentMonths = 0;

    /** 매입 상태 — APPROVED(승인만) / ACQUIRED(매입 완료) / UNSETTLED(미매입). */
    @Column(length = 20)
    private String acquiringStatus;

    /** 정산 예정일 (매입 마감 기준 카드사별 D+n). */
    @Column
    private LocalDate settlementDueDate;

    private static final String[] ISSUERS = {"신한카드", "삼성카드", "현대카드", "KB국민카드", "롯데카드", "BC카드", "우리카드", "하나카드"};

    /** 데모용: TID·승인시각에서 카드 상세를 결정적으로 채운다(실제로는 PG 승인 응답에서 옴). */
    public void enrichCardDetail() {
        if (!"CARD".equalsIgnoreCase(this.paymentMethod)) {
            this.acquiringStatus = "N/A";
            return;
        }
        int seed = this.tid == null ? this.orderNo.hashCode() : this.tid.hashCode();
        int h = Math.abs(seed);
        this.approvalNo = String.format("%08d", h % 100000000);
        this.issuerName = ISSUERS[h % ISSUERS.length];
        this.cardLast4 = String.format("%04d", (h / 7) % 10000);
        // 5만원 이상 결제의 일부만 할부(2/3개월)
        this.installmentMonths = (this.approvedAmount != null && this.approvedAmount.longValue() >= 50000 && h % 4 == 0)
                ? (h % 2 == 0 ? 3 : 2) : 0;
        LocalDateTime at = this.approvedAt == null ? LocalDateTime.now() : this.approvedAt;
        boolean acquired = at.isBefore(LocalDateTime.now().minusDays(1));
        this.acquiringStatus = this.paymentStatus == PaymentStatus.APPROVE_UNKNOWN ? "UNSETTLED"
                : acquired ? "ACQUIRED" : "APPROVED";
        this.settlementDueDate = at.toLocalDate().plusDays(2);
    }

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
    public void assignChannel(String channelType) { if (channelType != null && !channelType.isBlank()) this.channelType = channelType; }
    public void updatePaymentMethod(String paymentMethod) { if (paymentMethod != null && !paymentMethod.isBlank()) this.paymentMethod = paymentMethod; }
}
