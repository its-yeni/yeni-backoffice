package com.yeni.backoffice.api.config;

import com.yeni.backoffice.core.commerce.service.CommerceDeliveryService;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SettlementBatchRunRequest;
import com.yeni.backoffice.core.payment.service.SettlementOperationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 운영 자동화 배치를 운영자가 즉시 수동 실행하기 위한 엔드포인트.
 * 야간 스케줄러({@link PortfolioOperationsScheduler})와 같은 로직을 호출하되, 데모에서 7일을 기다릴 수 없으므로
 * 구매 확정은 "지금까지 배송 완료된 전 건"을 대상으로 한다.
 */
@RestController
@RequestMapping("/admin/api/ops")
public class OperationsSchedulerRestController {

    private final CommerceDeliveryService deliveryService;
    private final SettlementOperationService settlementOperationService;

    public OperationsSchedulerRestController(CommerceDeliveryService deliveryService,
            SettlementOperationService settlementOperationService) {
        this.deliveryService = deliveryService;
        this.settlementOperationService = settlementOperationService;
    }

    /** 배송 완료된 모든 주문을 구매 확정(정산 대상 전환). */
    @PostMapping("/run-auto-confirm")
    public ResponseEntity<Map<String, Object>> runAutoConfirm() {
        int confirmed = deliveryService.autoConfirmAgedDeliveries(LocalDateTime.now().plusSeconds(1));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("confirmed", confirmed);
        return ResponseEntity.ok(body);
    }

    /** 전 영업일·오늘의 확정 매출로 정산 초안 생성/재계산. */
    @PostMapping("/run-auto-settlement")
    public ResponseEntity<Map<String, Object>> runAutoSettlement() {
        Map<String, Object> body = new LinkedHashMap<>();
        int drafted = 0;
        for (LocalDate date : new LocalDate[]{LocalDate.now().minusDays(1), LocalDate.now()}) {
            try {
                settlementOperationService.runDailySettlement(new SettlementBatchRunRequest(date));
                drafted++;
            } catch (RuntimeException ignore) {
                // 이미 확정된 정산이 있거나 대상 매출이 없으면 건너뛴다.
            }
        }
        body.put("drafted", drafted);
        return ResponseEntity.ok(body);
    }
}
