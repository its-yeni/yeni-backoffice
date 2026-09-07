package com.yeni.backoffice.core.pos.entity;

import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import com.yeni.backoffice.core.pos.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="pos_client_log",indexes={
        @Index(name="idx_pos_log_terminal_occurred",columnList="terminalId,occurredAt"),
        @Index(name="idx_pos_log_level_category",columnList="logLevel,category")})
public class PosClientLog extends BaseTimeEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long terminalId;
    @Column(nullable=false) private Long storeId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=10) private PosLogLevel logLevel;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private PosLogCategory category;
    @Column(nullable=false) private LocalDateTime occurredAt;
    @Column(length=80) private String requestId;
    @Column(nullable=false,length=500) private String errorMessage;
    @Column(length=4000) private String stackTrace;
    @Column(nullable=false) private boolean retried;
    @Column private LocalDateTime retriedAt;

    public void markRetried(){retried=true;retriedAt=LocalDateTime.now();}
}
