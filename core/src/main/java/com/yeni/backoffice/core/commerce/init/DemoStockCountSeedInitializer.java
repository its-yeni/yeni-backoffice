package com.yeni.backoffice.core.commerce.init;

import com.yeni.backoffice.core.commerce.dto.StockCountDtos.CountLineResponse;
import com.yeni.backoffice.core.commerce.dto.StockCountDtos.StockCountResponse;
import com.yeni.backoffice.core.commerce.entity.CommerceStore;
import com.yeni.backoffice.core.commerce.repository.CommerceStoreRepository;
import com.yeni.backoffice.core.commerce.repository.StockCountRepository;
import com.yeni.backoffice.core.commerce.service.StockCountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 데모 재고 실사. 반영 완료 2건(일부 라인에 +1 / -2 차이 → 조정 전기)과 진행 중 1건을 만든다.
 * 완료 건은 {@link StockCountService#complete} 를 그대로 타므로 조정 이력이 입출고 내역에 남는다.
 */
@Component
@Profile("fly | demo")
@Order(190)
public class DemoStockCountSeedInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoStockCountSeedInitializer.class);

    private final TransactionTemplate tx;
    private final StockCountService stockCounts;
    private final StockCountRepository stockCountRepository;
    private final CommerceStoreRepository stores;

    public DemoStockCountSeedInitializer(PlatformTransactionManager txManager, StockCountService stockCounts,
            StockCountRepository stockCountRepository, CommerceStoreRepository stores) {
        this.tx = new TransactionTemplate(txManager);
        this.stockCounts = stockCounts;
        this.stockCountRepository = stockCountRepository;
        this.stores = stores;
    }

    @Override
    public void run(String... args) {
        try { seed(); } catch (Exception e) { log.warn("재고 실사 시드 스킵", e); }
    }

    private void seed() {
        if (read(() -> stockCountRepository.count()) > 0L) return;
        List<Long> storeIds = read(() -> stores.findAllByOrderByIdAsc().stream().map(CommerceStore::getId).toList());
        if (storeIds.isEmpty()) return;

        // 반영 완료 2건 — 라인 순번마다 다른 차이를 준다.
        for (int s = 0; s < Math.min(2, storeIds.size()); s++) {
            Long storeId = storeIds.get(s);
            int[] diffs = {0, -2, 0, 1, 0, 0, -1, 3};
            step(() -> {
                StockCountResponse count = stockCounts.start(storeId, "ALL", "월말 정기 실사");
                List<CountLineResponse> lines = count.lines();
                for (int i = 0; i < lines.size() && i < 8; i++) {
                    int delta = diffs[i % diffs.length];
                    if (delta == 0 && i % 3 != 0) continue; // 일부 라인은 미입력으로 남긴다
                    int counted = Math.max(0, lines.get(i).systemQuantity() + delta);
                    stockCounts.enterCount(count.id(), lines.get(i).id(), counted);
                }
                stockCounts.complete(count.id());
            });
        }

        // 진행 중 1건 — 라인 일부만 카운트.
        Long storeId = storeIds.get(storeIds.size() - 1);
        step(() -> {
            StockCountResponse count = stockCounts.start(storeId, "LOW_STOCK", "품절 임박 SKU 집중 실사");
            List<CountLineResponse> lines = count.lines();
            for (int i = 0; i < lines.size() && i < 3; i++) {
                stockCounts.enterCount(count.id(), lines.get(i).id(), lines.get(i).systemQuantity());
            }
        });
        log.info("데모 재고 실사 시딩 완료");
    }

    private void step(Runnable action) {
        try { tx.executeWithoutResult(status -> action.run()); }
        catch (Exception e) { log.debug("실사 시드 스텝 스킵", e); }
    }
    private <T> T read(java.util.function.Supplier<T> query) { return tx.execute(status -> query.get()); }
}
