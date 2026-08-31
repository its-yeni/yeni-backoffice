package com.yeni.backoffice.core.commerce.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="stock_transfer_item")
public class StockTransferItem {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private Long transferId;
 @Column(nullable=false) private Long variantId;
 @Column(nullable=false) private int quantity;
 @Column(nullable=false,precision=19,scale=2) private BigDecimal unitCost;
 /** 발송 시 출발 매장에서 FEFO로 소비한 대표 LOT 스냅샷 — 도착 매장 입고 시 유통기한 추적을 잇는다. */
 @Column(length=90) private String lotNo;
 private java.time.LocalDate expirationDate;
 public void recordLotSnapshot(String lotNo,java.time.LocalDate expirationDate){this.lotNo=lotNo;this.expirationDate=expirationDate;}
}
