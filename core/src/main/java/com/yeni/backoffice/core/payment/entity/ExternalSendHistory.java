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
@Table(name = "external_send_history")
public class ExternalSendHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sendRequestId;

    @Column(nullable = false, length = 40)
    private String targetSystem;

    @Column(columnDefinition = "text")
    private String requestBody;

    @Column(columnDefinition = "text")
    private String responseBody;

    @Column(nullable = false, length = 30)
    private String resultStatus;

    @Column(length = 200)
    private String resultMessage;

    @Column(nullable = false)
    private LocalDateTime sentAt;
}
