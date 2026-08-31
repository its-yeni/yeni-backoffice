package com.yeni.backoffice.core.commerce.entity;

import com.yeni.backoffice.core.commerce.enums.StockCountStatus;
import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.ValidationBusinessException;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** 재고 실사 헤더. 라인은 StockCountLine. */
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "stock_count", uniqueConstraints = @UniqueConstraint(name = "uk_stock_count_no", columnNames = "countNo"))
public class StockCount extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 40) private String countNo;
    @Column(nullable = false) private Long storeId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private StockCountStatus status;
    @Column(length = 300) private String memo;
    @Column(length = 40) private String actor;
    private LocalDateTime completedAt;

    public void complete() {
        requireInProgress();
        this.status = StockCountStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }
    public void cancel() {
        requireInProgress();
        this.status = StockCountStatus.CANCELED;
    }
    private void requireInProgress() {
        if (status != StockCountStatus.IN_PROGRESS)
            throw new ValidationBusinessException(ErrorCode.CONFLICT, "실사 중 상태에서만 처리할 수 있습니다.");
    }
}
