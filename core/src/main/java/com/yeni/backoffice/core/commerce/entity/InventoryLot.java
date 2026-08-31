package com.yeni.backoffice.core.commerce.entity;

import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "inventory_lot", uniqueConstraints = @UniqueConstraint(
        name = "uk_inventory_lot_store_variant_lot", columnNames = {"storeId", "variantId", "lotNo"}))
public class InventoryLot extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long storeId;
    @Column(nullable = false) private Long variantId;
    @Column(nullable = false, length = 80) private String lotNo;
    private LocalDate manufacturedDate;
    /** 유통기한이 없는 품목(의류 등)도 LOT 추적 대상이므로 nullable. */
    private LocalDate expirationDate;
    @Column(nullable = false) private int receivedQuantity;
    @Column(nullable = false) private int availableQuantity;
    @Column(length = 200) private String memo;

    public void receive(int quantity, LocalDate manufacturedDate, LocalDate expirationDate, String memo) {
        this.receivedQuantity += quantity;
        this.availableQuantity += quantity;
        if (manufacturedDate != null) this.manufacturedDate = manufacturedDate;
        if (expirationDate != null) this.expirationDate = expirationDate;
        if (memo != null && !memo.isBlank()) this.memo = memo.trim();
    }
    public int consumeUpTo(int requested) {
        int consumed = Math.min(Math.max(requested, 0), availableQuantity);
        availableQuantity -= consumed;
        return consumed;
    }
}
