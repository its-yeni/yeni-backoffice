package com.yeni.backoffice.core.commerce.entity;

import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import com.yeni.backoffice.core.commerce.enums.StockTransferStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="stock_transfer",uniqueConstraints=@UniqueConstraint(name="uk_stock_transfer_no",columnNames="transferNo"))
public class StockTransfer extends BaseTimeEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=60) private String transferNo;
 @Column(nullable=false) private Long sourceStoreId;
 @Column(nullable=false) private Long destinationStoreId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private StockTransferStatus status;
 @Column(length=200) private String reason;
 @Column(length=60) private String actor;
 private LocalDateTime shippedAt;
 private LocalDateTime receivedAt;
 public void ship(){status=StockTransferStatus.IN_TRANSIT;shippedAt=LocalDateTime.now();}
 public void receive(){status=StockTransferStatus.RECEIVED;receivedAt=LocalDateTime.now();}
 public void cancel(){status=StockTransferStatus.CANCELLED;}
}
