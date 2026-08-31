package com.yeni.backoffice.core.payment.entity;
import com.yeni.backoffice.core.payment.enums.PgReconciliationStatus;
import com.yeni.backoffice.core.payment.enums.PgReconciliationResolutionStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Getter @Builder @NoArgsConstructor @AllArgsConstructor @Entity @Table(name="pg_settlement_reconciliation")
public class PgSettlementReconciliation {@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;@Column(nullable=false) private Long importId;private Long salesTransactionId;@Column(length=120) private String tid;@Column(length=100) private String orderNo;@Column(precision=19,scale=2) private BigDecimal internalAmount;@Column(precision=19,scale=2) private BigDecimal pgAmount;@Column(precision=19,scale=2) private BigDecimal pgFee;@Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private PgReconciliationStatus status;@Column(length=300) private String reason;@Enumerated(EnumType.STRING) @Column(nullable=false,length=20) @Builder.Default private PgReconciliationResolutionStatus resolutionStatus=PgReconciliationResolutionStatus.NOT_REQUIRED;@Column(length=80) private String assignee;@Column(length=500) private String resolutionNote;private LocalDateTime resolvedAt;public void startReview(String assignee,String note){this.resolutionStatus=PgReconciliationResolutionStatus.IN_REVIEW;this.assignee=assignee;this.resolutionNote=note;this.resolvedAt=null;}public void resolve(PgReconciliationResolutionStatus status,String assignee,String note){this.resolutionStatus=status;this.assignee=assignee;this.resolutionNote=note;this.resolvedAt=LocalDateTime.now();}}
