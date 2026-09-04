package com.yeni.backoffice.core.payment.entity;

import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "audit_log")
public class AuditLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String domainType;

    @Column(nullable = false, length = 40)
    private String actionType;

    @Column(length = 80)
    private String referenceKey;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 60)
    @Builder.Default
    private String actor = "SYSTEM";

    @Column(length = 30)
    @Builder.Default
    private String resultStatus = "SUCCESS";

    @Column(length = 80)
    private String requestId;

    @Column(columnDefinition = "text")
    private String beforeValue;

    @Column(columnDefinition = "text")
    private String afterValue;

    @Column(nullable = false)
    private LocalDateTime loggedAt;
}
