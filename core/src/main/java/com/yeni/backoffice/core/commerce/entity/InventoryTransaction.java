package com.yeni.backoffice.core.commerce.entity;

import com.yeni.backoffice.core.commerce.enums.InventoryTransactionType;
import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * SKU(ProductVariant) 재고 변동 이력. 입고/예약/예약해제/출고/조정 등 현재재고(stock) 또는 예약재고(reserved)가
 * 바뀔 때마다 한 건씩 남긴다. before/after 쌍 대신, 이 변동이 반영된 "직후" 시점의 현재재고·예약재고 스냅샷을
 * 함께 저장한다 — RESERVE/RELEASE는 stock은 그대로고 reserved만 바뀌고, SHIPMENT는 반대로 둘 다 바뀌는 등
 * 유형마다 어느 값이 바뀌는지 달라서, 단일 before/after 쌍보다 두 값의 사후 스냅샷이 더 명확하다.
 */
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "inventory_transaction")
public class InventoryTransaction extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long variantId;
    @Column(nullable = false, length = 100) private String sku;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private InventoryTransactionType type;
    @Column(nullable = false) private int quantity;
    @Column(nullable = false) private int stockBefore;
    @Column(nullable = false) private int stockAfter;
    @Column(nullable = false) private int reservedBefore;
    @Column(nullable = false) private int reservedAfter;
    @Column(length = 200) private String reason;
    @Column(length = 40) private String referenceType;
    @Column private Long referenceId;
    @Column(length = 40) private String actor;
}
