package com.yeni.backoffice.core.payment.entity;

import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import com.yeni.backoffice.core.payment.enums.LogResultStatus;
import com.yeni.backoffice.core.payment.enums.PaymentEventType;
import com.yeni.backoffice.core.payment.enums.PgApiType;
import com.yeni.backoffice.core.payment.enums.PgCompany;
import com.yeni.backoffice.core.payment.enums.PgProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "pg_api_log")
public class PgApiLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100)
    private String requestId;

    @Column
    private Long paymentId;

    @Column(length = 80)
    private String orderNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PgCompany pgCompany;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private PgProvider pgProvider;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private PaymentEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PgApiType apiType;

    @Column(length = 120)
    private String tid;

    @Column(length = 120)
    private String idempotencyKey;

    @Column(columnDefinition = "text")
    private String requestBody;

    @Column(columnDefinition = "text")
    private String responseBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LogResultStatus resultStatus;

    @Column(length = 200)
    private String resultMessage;

    private Integer httpStatus;

    private Boolean successYn;

    private Long durationMs;

    @Column(length = 300)
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime loggedAt;

    public void complete(String responseBody, LogResultStatus resultStatus, String resultMessage) {
        this.responseBody = responseBody;
        this.resultStatus = resultStatus;
        this.resultMessage = resultMessage;
        this.successYn = LogResultStatus.SUCCESS.equals(resultStatus);
        this.loggedAt = LocalDateTime.now();
    }

    public void complete(String responseBody, LogResultStatus resultStatus, String resultMessage, String tid) {
        complete(responseBody, resultStatus, resultMessage);
        this.tid = tid;
    }
}
