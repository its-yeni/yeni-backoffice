package com.yeni.backoffice.api.config;

import com.yeni.backoffice.core.commerce.service.CommerceDeliveryService;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SettlementBatchRunRequest;
import com.yeni.backoffice.core.payment.service.SettlementOperationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 운영 자동화 스케줄러.
 * <ol>
 *   <li>구매 확정 자동 전환 — 배송 완료 후 {@code portfolio.settlement.auto-confirm-grace-days}일이 지난 주문의
 *       매출을 자동으로 확정한다(정산 대상 전환). 실무의 "배송 완료 + N일 자동 구매확정"에 대응한다.</li>
 *   <li>정산 초안 자동 생성 — 전 영업일의 확정 매출을 모아 정산 초안(DRAFT)을 만든다.</li>
 * </ol>
 * 각 작업은 개별 실패가 다음 실행이나 다른 작업을 막지 않도록 예외를 삼키고 로그만 남긴다.
 */
@Component
public class PortfolioOperationsScheduler {

    private static final Logger log = LoggerFactory.getLogger(PortfolioOperationsScheduler.class);

    private final CommerceDeliveryService deliveryService;
    private final SettlementOperationService settlementOperationService;
    private final boolean autoConfirmEnabled;
    private final int autoConfirmGraceDays;
    private final boolean autoDraftEnabled;

    public PortfolioOperationsScheduler(
            CommerceDeliveryService deliveryService,
            SettlementOperationService settlementOperationService,
            @Value("${portfolio.settlement.auto-confirm-enabled:true}") boolean autoConfirmEnabled,
            @Value("${portfolio.settlement.auto-confirm-grace-days:7}") int autoConfirmGraceDays,
            @Value("${portfolio.settlement.auto-draft-enabled:true}") boolean autoDraftEnabled) {
        this.deliveryService = deliveryService;
        this.settlementOperationService = settlementOperationService;
        this.autoConfirmEnabled = autoConfirmEnabled;
        this.autoConfirmGraceDays = autoConfirmGraceDays;
        this.autoDraftEnabled = autoDraftEnabled;
    }

    /** 매일 01:10 — 배송 완료 후 유예 기간이 지난 주문을 구매 확정 처리한다. */
    @Scheduled(cron = "${portfolio.settlement.auto-confirm-cron:0 10 1 * * *}")
    public void autoConfirmDeliveredOrders() {
        if (!autoConfirmEnabled) return;
        LocalDateTime threshold = LocalDateTime.now().minusDays(Math.max(0, autoConfirmGraceDays));
        try {
            int confirmed = deliveryService.autoConfirmAgedDeliveries(threshold);
            if (confirmed > 0) {
                log.info("구매 확정 자동 전환 완료: {}건 (기준일시 {} 이전 배송 완료)", confirmed, threshold);
            }
        } catch (Exception e) {
            log.error("구매 확정 자동 전환 실패 — 다음 실행에서 재시도됩니다.", e);
        }
    }

    /** 매일 02:10 — 전 영업일의 확정 매출로 정산 초안을 생성한다. */
    @Scheduled(cron = "${portfolio.settlement.auto-draft-cron:0 10 2 * * *}")
    public void autoDraftSettlement() {
        if (!autoDraftEnabled) return;
        LocalDate targetDate = LocalDate.now().minusDays(1);
        try {
            settlementOperationService.runDailySettlement(new SettlementBatchRunRequest(targetDate));
            log.info("정산 초안 자동 생성 완료: 영업일 {}", targetDate);
        } catch (Exception e) {
            // 이미 확정(CONFIRMED/PAID)된 정산이 있거나 동시 실행이면 중복 실행 예외가 난다 — 정상 흐름이므로 debug.
            log.debug("정산 초안 자동 생성 건너뜀 (영업일 {}): {}", targetDate, e.getMessage());
        }
    }
}
