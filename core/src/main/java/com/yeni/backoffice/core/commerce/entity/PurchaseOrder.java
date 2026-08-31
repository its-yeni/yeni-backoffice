package com.yeni.backoffice.core.commerce.entity;

import com.yeni.backoffice.core.commerce.enums.PurchaseOrderStatus;
import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.ValidationBusinessException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 발주서 헤더. 품목은 PurchaseOrderItem. */
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "purchase_order", uniqueConstraints = @UniqueConstraint(name = "uk_purchase_order_no", columnNames = "poNo"))
public class PurchaseOrder extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 40) private String poNo;
    @Column(nullable = false) private Long supplierId;
    /** 입고 대상 매장(창고). */
    @Column(nullable = false) private Long storeId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private PurchaseOrderStatus status;
    private LocalDate expectedArrivalDate;
    private LocalDateTime orderedAt;
    @Column(nullable = false, precision = 19, scale = 2) @org.hibernate.annotations.ColumnDefault("0") private BigDecimal totalAmount;
    @Column(length = 300) private String memo;
    @Column(length = 40) private String actor;

    public void updateHeader(Long supplierId, Long storeId, LocalDate expectedArrivalDate, String memo) {
        requireDraft();
        this.supplierId = supplierId;
        this.storeId = storeId;
        this.expectedArrivalDate = expectedArrivalDate;
        this.memo = memo == null || memo.isBlank() ? null : memo.trim();
    }
    public void applyTotal(BigDecimal total) { this.totalAmount = total == null ? BigDecimal.ZERO : total; }

    public void place() {
        requireDraft();
        this.status = PurchaseOrderStatus.ORDERED;
        this.orderedAt = LocalDateTime.now();
    }
    public void cancel() {
        if (status == PurchaseOrderStatus.RECEIVED || status == PurchaseOrderStatus.CANCELED)
            throw new ValidationBusinessException(ErrorCode.CONFLICT, "이미 입고 완료되었거나 취소된 발주입니다.");
        this.status = PurchaseOrderStatus.CANCELED;
    }
    /** 품목 입고 반영 후 전체 입고율에 따라 상태를 재계산한다. */
    public void recalcReceiptStatus(boolean allReceived, boolean anyReceived) {
        if (status == PurchaseOrderStatus.CANCELED) return;
        if (allReceived) this.status = PurchaseOrderStatus.RECEIVED;
        else if (anyReceived) this.status = PurchaseOrderStatus.PARTIALLY_RECEIVED;
    }
    public boolean isReceivable() {
        return status == PurchaseOrderStatus.ORDERED || status == PurchaseOrderStatus.PARTIALLY_RECEIVED;
    }
    private void requireDraft() {
        if (status != PurchaseOrderStatus.DRAFT)
            throw new ValidationBusinessException(ErrorCode.CONFLICT, "작성 중(DRAFT) 발주만 수정할 수 있습니다.");
    }
}
