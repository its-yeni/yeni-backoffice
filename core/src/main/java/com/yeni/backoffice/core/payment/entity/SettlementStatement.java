package com.yeni.backoffice.core.payment.entity;

import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import com.yeni.backoffice.core.payment.enums.SettlementStatus;
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
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "settlement_statement",
        uniqueConstraints = @UniqueConstraint(name = "uk_settlement_statement_date_mid_store", columnNames = {"settlementDate", "mid", "storeId"})
)
public class SettlementStatement extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long storeId;

    @Column(nullable = false)
    private LocalDate settlementDate;

    @Column(nullable = false, length = 40)
    private String pgCompany;

    @Column(nullable = false, length = 40)
    private String mid;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal grossAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal feeAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal vatAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal netAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal adjustmentAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal holdAmount = BigDecimal.ZERO;

    private LocalDate scheduledPayoutDate;

    private LocalDateTime paidAt;

    @Column(length = 80)
    private String payoutReference;

    @Column(length = 80)
    private String payoutAccountMasked;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SettlementStatus settlementStatus;

    /** 승인 대기 단계 — NONE / CONFIRM_REQUESTED / PAYOUT_REQUESTED (maker-checker). */
    @Builder.Default
    @Column(length = 20)
    private String approvalStage = "NONE";

    /** 확정/지급을 요청한 담당자. */
    @Column(length = 40)
    private String requestedBy;

    private LocalDateTime requestedAt;

    public void requestApproval(String stage, String actor, LocalDateTime at) {
        this.approvalStage = stage;
        this.requestedBy = actor;
        this.requestedAt = at;
    }

    private void clearApproval() {
        this.approvalStage = "NONE";
        this.requestedBy = null;
        this.requestedAt = null;
    }

    public void confirm() {
        this.settlementStatus = SettlementStatus.CONFIRMED;
        clearApproval();
    }

    public void markPaid(String payoutReference, String payoutAccountMasked, LocalDateTime paidAt) {
        this.settlementStatus = SettlementStatus.PAID;
        this.payoutReference = payoutReference;
        this.payoutAccountMasked = payoutAccountMasked;
        this.paidAt = paidAt;
        clearApproval();
    }

    public void applyAdjustment(
            BigDecimal adjustmentAmount,
            BigDecimal holdAmount,
            LocalDate scheduledPayoutDate) {
        this.adjustmentAmount = adjustmentAmount == null ? BigDecimal.ZERO : adjustmentAmount;
        this.holdAmount = holdAmount == null ? BigDecimal.ZERO : holdAmount;
        this.scheduledPayoutDate = scheduledPayoutDate;
        this.netAmount = grossAmount.subtract(feeAmount).subtract(vatAmount)
                .add(this.adjustmentAmount).subtract(this.holdAmount);
    }

    public void recalculate(
            BigDecimal grossAmount,
            BigDecimal feeAmount,
            BigDecimal vatAmount,
            BigDecimal netAmount) {
        this.grossAmount = grossAmount;
        this.feeAmount = feeAmount;
        this.vatAmount = vatAmount;
        this.netAmount = netAmount
                .add(adjustmentAmount == null ? BigDecimal.ZERO : adjustmentAmount)
                .subtract(holdAmount == null ? BigDecimal.ZERO : holdAmount);
    }
}
