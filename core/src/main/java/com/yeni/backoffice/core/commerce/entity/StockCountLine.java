package com.yeni.backoffice.core.commerce.entity;

import jakarta.persistence.*;
import lombok.*;

/** 실사 라인 — 시작 시점 시스템 재고 스냅샷과 실사 카운트, 그 차이. */
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "stock_count_line",
        uniqueConstraints = @UniqueConstraint(name = "uk_stock_count_line", columnNames = {"stockCountId", "variantId"}))
public class StockCountLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long stockCountId;
    @Column(nullable = false) private Long variantId;
    @Column(nullable = false) private int systemQuantity;
    /** 실사로 센 수량. 입력 전에는 null. */
    private Integer countedQuantity;
    @Column(nullable = false) @org.hibernate.annotations.ColumnDefault("0") private int differenceQuantity;

    public void enterCount(int counted) {
        this.countedQuantity = Math.max(0, counted);
        this.differenceQuantity = this.countedQuantity - systemQuantity;
    }
    public boolean counted() { return countedQuantity != null; }
}
