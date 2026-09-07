package com.yeni.backoffice.core.pos.entity;

import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import com.yeni.backoffice.core.pos.enums.PosSyncResource;
import com.yeni.backoffice.core.pos.enums.PosSyncStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name="pos_sync_execution",uniqueConstraints=@UniqueConstraint(name="uk_pos_sync_terminal_run",columnNames={"terminalId","clientRunId"}))
public class PosSyncExecution extends BaseTimeEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long terminalId;
    @Column(nullable=false) private Long storeId;
    @Column(nullable=false,length=80) private String clientRunId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private PosSyncResource resourceType;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private PosSyncStatus status;
    @Column(length=200) private String cursorValue;
    @Column(nullable=false) private int successCount;
    @Column(nullable=false) private int failureCount;
    @Column(nullable=false) private int retryCount;
    @Column(length=60) private String errorCode;
    @Column(length=500) private String errorMessage;
    @Column(nullable=false) private LocalDateTime reportedAt;
    @Column private LocalDateTime completedAt;

    public void requestRetry(){
        if(status==PosSyncStatus.FAILED){status=PosSyncStatus.RETRY_REQUESTED;retryCount++;}
    }
    public void resolve(){if(status==PosSyncStatus.RETRY_REQUESTED)status=PosSyncStatus.RESOLVED;}
}
