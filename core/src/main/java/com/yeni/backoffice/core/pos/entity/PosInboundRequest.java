package com.yeni.backoffice.core.pos.entity;

import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import com.yeni.backoffice.core.pos.enums.PosRequestStatus;
import com.yeni.backoffice.core.pos.enums.PosRequestType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(name = "pos_inbound_request", uniqueConstraints =
        @UniqueConstraint(name = "uk_pos_request_terminal_client", columnNames = {"terminalId", "clientRequestId"}))
public class PosInboundRequest extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long terminalId;

    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false, length = 80)
    private String clientRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PosRequestType requestType;

    @Column(nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PosRequestStatus status;

    @Column(length = 80)
    private String serverReference;

    @Column(length = 60)
    private String errorCode;

    @Column(length = 500)
    private String errorMessage;

    @Column(nullable = false)
    private int retryCount;

    @Column
    private LocalDateTime completedAt;

    public void markProcessing() {
        status = PosRequestStatus.PROCESSING;
    }

    public void markSucceeded(String serverReference, LocalDateTime completedAt) {
        this.status = PosRequestStatus.SUCCEEDED;
        this.serverReference = serverReference;
        this.errorCode = null;
        this.errorMessage = null;
        this.completedAt = completedAt;
    }

    public void markFailed(String errorCode, String errorMessage, int maxRetryCount) {
        retryCount++;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.status = retryCount >= maxRetryCount ? PosRequestStatus.MANUAL_REVIEW : PosRequestStatus.FAILED;
    }
}
