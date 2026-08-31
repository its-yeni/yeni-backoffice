package com.yeni.backoffice.core.commerce.entity;

import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.ValidationBusinessException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "purchase_order_item")
public class PurchaseOrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long purchaseOrderId;
    @Column(nullable = false) private Long variantId;
    @Column(nullable = false) private int orderedQuantity;
    @Column(nullable = false) @org.hibernate.annotations.ColumnDefault("0") private int receivedQuantity;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal unitCost;

    public int outstandingQuantity() { return Math.max(0, orderedQuantity - receivedQuantity); }
    public boolean fullyReceived() { return receivedQuantity >= orderedQuantity; }

    /** 이번 입고 수량을 누적한다. 발주 수량 초과 입고는 막는다. */
    public void receive(int quantity) {
        if (quantity <= 0) throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "입고 수량은 1개 이상이어야 합니다.");
        if (receivedQuantity + quantity > orderedQuantity)
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR,
                    "발주 수량을 초과해 입고할 수 없습니다. (발주 " + orderedQuantity + " / 기입고 " + receivedQuantity + ")");
        this.receivedQuantity += quantity;
    }
    public BigDecimal lineAmount() { return unitCost.multiply(BigDecimal.valueOf(orderedQuantity)); }
}
