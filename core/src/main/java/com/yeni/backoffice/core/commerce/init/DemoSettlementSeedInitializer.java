package com.yeni.backoffice.core.commerce.init;

import com.yeni.backoffice.core.commerce.repository.CommerceStoreRepository;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SettlementBatchRunRequest;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SettlementPayRequest;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SettlementStatementResponse;
import com.yeni.backoffice.core.payment.repository.SettlementStatementRepository;
import com.yeni.backoffice.core.payment.service.SettlementOperationService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

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
    }
}
