package com.yeni.backoffice.core.commerce.init;

import com.yeni.backoffice.core.commerce.entity.CommerceStore;
import com.yeni.backoffice.core.commerce.repository.CommerceStoreRepository;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SettlementStatementResponse;
import com.yeni.backoffice.core.payment.repository.PgSettlementImportRepository;
import com.yeni.backoffice.core.payment.service.PgSettlementReconciliationService;
import com.yeni.backoffice.core.payment.service.SettlementOperationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 데모 PG 정산 대사. 이미 만들어진 일자별 정산 명세를 기준으로 외부 PG 정산 CSV를 생성·업로드해
 * PG 정산 대사 화면이 일치/불일치 행을 함께 보여주게 한다. ({@link DemoSettlementSeedInitializer} 이후 실행)
 */
@Component
@Profile("fly | demo")
@Order(210)
public class DemoPgReconciliationSeedInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoPgReconciliationSeedInitializer.class);

    private final TransactionTemplate tx;
    private final SettlementOperationService settlements;
    private final PgSettlementReconciliationService reconciliation;
    private final PgSettlementImportRepository imports;
    private final CommerceStoreRepository stores;

    public DemoPgReconciliationSeedInitializer(PlatformTransactionManager txManager, SettlementOperationService settlements,
            PgSettlementReconciliationService reconciliation, PgSettlementImportRepository imports,
            CommerceStoreRepository stores) {
        this.tx = new TransactionTemplate(txManager);
        this.settlements = settlements;
        this.reconciliation = reconciliation;
        this.imports = imports;
        this.stores = stores;
    }

    @Override
    public void run(String... args) {
        try { seed(); } catch (Exception e) { log.warn("PG 정산 대사 시드 스킵", e); }
    }

    private void seed() {
        if (read(() -> imports.count()) > 0L) return;
        Long shopStoreId = read(() -> stores.findByStoreCode("YENI-SHOP-01").map(CommerceStore::getId).orElse(null));
        List<SettlementStatementResponse> statements = read(() ->
                settlements.getStatements(LocalDate.now().minusDays(30), LocalDate.now(), shopStoreId).stream()
                        .filter(s -> s.saleAmount() != null && s.saleAmount().compareTo(BigDecimal.ZERO) > 0)
                        .limit(3).toList());
        if (statements.isEmpty()) return;

        for (int i = 0; i < statements.size(); i++) {
            SettlementStatementResponse stmt = statements.get(i);
            boolean mismatch = i == statements.size() - 1;   // 마지막 1건은 금액 불일치 포함
            step(() -> {
                String csv = reconciliation.sampleCsv(stmt.settlementDate(), mismatch);
                reconciliation.importCsv(
                        "pg-settlement-" + stmt.settlementDate() + (mismatch ? "-mismatch" : "") + ".csv",
                        stmt.pgCompany(), stmt.mid(), stmt.settlementDate(), csv);
            });
        }
        log.info("데모 PG 정산 대사 시딩 완료");
    }

    private void step(Runnable action) {
        try { tx.executeWithoutResult(status -> action.run()); }
        catch (Exception e) { log.debug("PG 정산 대사 시드 스텝 스킵", e); }
    }
    private <T> T read(java.util.function.Supplier<T> query) { return tx.execute(status -> query.get()); }
}
