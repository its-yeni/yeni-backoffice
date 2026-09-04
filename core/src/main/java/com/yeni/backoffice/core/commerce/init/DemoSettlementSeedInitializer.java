package com.yeni.backoffice.core.commerce.init;

import com.yeni.backoffice.core.commerce.repository.CommerceStoreRepository;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SettlementBatchRunRequest;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SettlementPayRequest;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SettlementStatementResponse;
import com.yeni.backoffice.core.payment.entity.SettlementStatement;
import com.yeni.backoffice.core.payment.enums.SettlementStatus;
import com.yeni.backoffice.core.payment.repository.SettlementStatementRepository;
import com.yeni.backoffice.core.payment.service.SettlementOperationService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/** 정산 목록·페이지 이동·상태 전이(초안→확정→지급 완료)를 시연하기 위한 연결 데이터. */
@Component
@Profile("fly | demo")
@Order(200)
public class DemoSettlementSeedInitializer implements CommandLineRunner {
    private static final int SETTLEMENT_DAYS = 14;

    private final SettlementStatementRepository statements;
    private final CommerceStoreRepository stores;
    private final SettlementOperationService settlements;

    public DemoSettlementSeedInitializer(SettlementStatementRepository statements,
                                         CommerceStoreRepository stores,
                                         SettlementOperationService settlements) {
        this.statements = statements;
        this.stores = stores;
        this.settlements = settlements;
    }

    @Override
    public void run(String... args) {
        if (statements.count() >= SETTLEMENT_DAYS) return;
        stores.findByStoreCode("YENI-SHOP-01").ifPresent(store -> {
            for (int daysAgo = SETTLEMENT_DAYS - 1; daysAgo >= 0; daysAgo--) {
                try {
                    SettlementStatementResponse statement = settlements.runDailySettlement(new SettlementBatchRunRequest(
                            LocalDate.now().minusDays(daysAgo), store.getId()));
                    // 오래된 건일수록 정산이 더 진행된 상태로 둔다: 5일 이전 확정, 9일 이전 지급 완료.
                    if (statement != null && statement.id() != null) {
                        if (daysAgo >= 5) {
                            settlements.confirmStatement(statement.id());
                        }
                        if (daysAgo >= 9) {
                            settlements.markPaid(statement.id(), new SettlementPayRequest(
                                    "PAYOUT-" + LocalDate.now().minusDays(daysAgo), "가상 정산계좌 000-****-****21"));
                        }
                    }
                } catch (Exception ignored) {
                    // 데모 보강 실패가 애플리케이션 기동을 막지 않도록 한다.
                }
            }
        });
        promoteDraftStatuses();
    }

    /**
     * 데모 주문이 모두 최근 날짜라 위 루프의 날짜 기준(5일/9일)으로는 상태 전이가 걸리지 않는다.
     * 목록·상태 전이·회계 분개 시연이 가능하도록, 생성된 DRAFT 정산 중 앞쪽 절반을 확정/지급 완료로 올린다.
     */
    private void promoteDraftStatuses() {
        List<SettlementStatement> drafts = statements.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .filter(s -> s.getSettlementStatus() == SettlementStatus.DRAFT)
                .filter(s -> s.getGrossAmount() != null && s.getGrossAmount().signum() > 0)
                .toList();
        if (drafts.isEmpty()) return;

        // 화면과 반복 가능한 E2E에서 확정 가능한 초안을 항상 하나 이상 남긴다.
        int promotableCount = Math.max(0, drafts.size() - 1);
        int paidCount = promotableCount >= 2 ? Math.max(1, promotableCount / 2) : 0;
        int confirmCount = promotableCount - paidCount;
        for (int i = 0; i < drafts.size(); i++) {
            Long id = drafts.get(i).getId();
            try {
                if (i < paidCount) {
                    settlements.confirmStatement(id);
                    settlements.markPaid(id, new SettlementPayRequest(
                            "PAYOUT-DEMO-" + id, "가상 정산계좌 000-****-****21"));
                } else if (i < paidCount + confirmCount) {
                    settlements.confirmStatement(id);
                }
            } catch (Exception ignored) {
                // 개별 전이 실패는 무시한다.
            }
        }
    }
}
