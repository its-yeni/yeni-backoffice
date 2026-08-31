package com.yeni.backoffice.core.payment.entity;

import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import com.yeni.backoffice.core.payment.enums.LedgerStatus;
import com.yeni.backoffice.core.payment.enums.SaleStatus;
import com.yeni.backoffice.core.payment.enums.SaleType;
import com.yeni.backoffice.core.payment.enums.SalesSettlementStatus;
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
        name = "sales_transaction",
        uniqueConstraints = @UniqueConstraint(name = "uk_sales_transaction_source", columnNames = {"sourceType", "sourceId"})
)
public class SalesTransaction extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long storeId;

    @Column(nullable = false, length = 20)
    private String sourceType;

    @Column(nullable = false)
    private Long sourceId;

    private Long paymentId;

    private Long cancelId;

    private Long originalSalesTransactionId;

    @Column(nullable = false, length = 80)
    private String orderNo;

    @Column(nullable = false, length = 120)
    private String tid;

    @Column(length = 120)
    private String pgTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SaleType saleType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal saleAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal supplyAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal vatAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SaleStatus saleStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LedgerStatus ledgerStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SalesSettlementStatus settlementStatus;

    @Column(nullable = false)
    private LocalDate businessDate;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Column(length = 40)
    private String pgCode;

    @Column(length = 40)
    private String paymentMethod;

    private Long sellerId;

    private Long orderItemId;

    @Builder.Default
    @Column(nullable = false)
    private Boolean externalSendRequired = true;

    @Builder.Default
    @Column(nullable = false)
    private Boolean settlementIncludedYn = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean confirmedYn = false;

    private LocalDateTime confirmedAt;

    public void confirm(LocalDateTime confirmedAt) {
        if (Boolean.TRUE.equals(this.confirmedYn)) return;
        this.confirmedYn = true;
        this.confirmedAt = confirmedAt == null ? LocalDateTime.now() : confirmedAt;
    }

    public void markIncludedInSettlement() {
        this.settlementIncludedYn = true;
        this.settlementStatus = SalesSettlementStatus.CALCULATED;
    }

    public void markSettled() {
        this.saleStatus = SaleStatus.SETTLED;
        this.settlementStatus = SalesSettlementStatus.SETTLED;
    }

    public void markPaid() {
        this.settlementStatus = SalesSettlementStatus.PAID;
    }

    public void markCarriedOver() {
        this.settlementStatus = SalesSettlementStatus.CARRIED_OVER;
    }

    public void assignStore(Long storeId) { this.storeId = storeId; }
}
