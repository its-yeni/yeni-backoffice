package com.yeni.backoffice.core.payment.entity;
import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
@Getter @Builder @NoArgsConstructor @AllArgsConstructor @Entity @Table(name="pg_settlement_import")
public class PgSettlementImport extends BaseTimeEntity {@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;@Column(nullable=false,length=120) private String fileName;@Column(nullable=false,length=40) private String pgCompany;@Column(nullable=false,length=80) private String mid;@Column(nullable=false) private LocalDate businessDate;@Column(nullable=false) private int totalCount;@Column(nullable=false) private int matchedCount;@Column(nullable=false) private int mismatchCount;@Column(nullable=false,length=40) private String status;public void complete(int total,int matched,int mismatch){totalCount=total;matchedCount=matched;mismatchCount=mismatch;status=mismatch==0?"COMPLETED":"REVIEW_REQUIRED";}}
