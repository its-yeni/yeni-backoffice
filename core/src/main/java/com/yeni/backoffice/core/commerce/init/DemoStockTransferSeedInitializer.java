package com.yeni.backoffice.core.commerce.init;

import com.yeni.backoffice.core.commerce.dto.StockTransferDtos.TransferCreateRequest;
import com.yeni.backoffice.core.commerce.dto.StockTransferDtos.TransferItemRequest;
import com.yeni.backoffice.core.commerce.dto.StockTransferDtos.TransferResponse;
import com.yeni.backoffice.core.commerce.entity.CommerceStore;
import com.yeni.backoffice.core.commerce.entity.Product;
import com.yeni.backoffice.core.commerce.entity.ProductVariant;
import com.yeni.backoffice.core.commerce.enums.StoreBusinessType;
import com.yeni.backoffice.core.commerce.repository.*;
import com.yeni.backoffice.core.commerce.service.StockTransferService;
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
 * 데모 재고 이동 전표. 대표 온라인몰에서 다른 온라인몰 채널로 소량 이동을 만들어
 * 재고 이동 화면이 요청/이동 중/입고 완료 상태를 모두 보여주게 한다.
 */
@Component
@Profile("fly | demo")
@Order(192)
public class DemoStockTransferSeedInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoStockTransferSeedInitializer.class);

    private final TransactionTemplate tx;
    private final StockTransferService transferService;
    private final StockTransferRepository transfers;
    private final CommerceStoreRepository stores;
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final StoreVariantInventoryRepository storeInventories;

    public DemoStockTransferSeedInitializer(PlatformTransactionManager txManager, StockTransferService transferService,
            StockTransferRepository transfers, CommerceStoreRepository stores, ProductRepository products,
            ProductVariantRepository variants, StoreVariantInventoryRepository storeInventories) {
        this.tx = new TransactionTemplate(txManager);
        this.transferService = transferService;
        this.transfers = transfers;
        this.stores = stores;
        this.products = products;
        this.variants = variants;
        this.storeInventories = storeInventories;
    }

    @Override
    public void run(String... args) {
        try { seed(); } catch (Exception e) { log.warn("재고 이동 시드 스킵", e); }
    }

    private void seed() {
        if (read(() -> transfers.count()) > 0L) return;
        Long[] pick = read(() -> {
            List<CommerceStore> retail = stores.findAllByOrderByIdAsc().stream()
                    .filter(s -> s.getBusinessType() == StoreBusinessType.ONLINE_RETAIL).toList();
            if (retail.size() < 2) return null;
            return new Long[]{retail.get(0).getId(), retail.get(1).getId(), retail.size() > 2 ? retail.get(2).getId() : retail.get(1).getId()};
        });
        if (pick == null) return;
        Long source = pick[0];
        Long[] dests = {pick[1], pick[2], pick[1], pick[2], pick[1]};

        List<Long> sourceVariants = read(() -> {
            String sc = stores.findById(source).map(CommerceStore::getStoreCode).orElse("");
            java.util.Set<Long> productIds = products.findByStoreCode(sc).stream().map(Product::getId)
                    .collect(java.util.stream.Collectors.toSet());
            return variants.findAllByOrderByProductIdAscSortOrderAscIdAsc().stream()
                    .filter(v -> productIds.contains(v.getProductId()))
                    .filter(v -> storeInventories.findByStoreIdAndVariantId(source, v.getId())
                            .map(i -> i.getAvailableQuantity() >= 12).orElse(false))
                    .map(ProductVariant::getId).toList();
        });
        if (sourceVariants.size() < 5) return;

        String[] reasons = {"아울렛 채널 재고 보충", "패션관 시즌 상품 이관", "리빙관 신규 진열용", "기프트관 프로모션 대비", "채널 간 재고 균형 조정"};
        for (int i = 0; i < 5; i++) {
            int idx = i;
            step(() -> {
                TransferResponse t = transferService.create(new TransferCreateRequest(source, dests[idx],
                        List.of(new TransferItemRequest(sourceVariants.get(idx), 5 + idx)), reasons[idx]));
                if (idx <= 1) return;            // 0,1 → 요청 상태 유지
                transferService.ship(t.id());   // 2,3,4 → 이동 중
                if (idx == 4) transferService.receive(t.id());  // 4 → 입고 완료
            });
        }
        log.info("데모 재고 이동 시딩 완료");
    }

    private void step(Runnable action) {
        try { tx.executeWithoutResult(status -> action.run()); }
        catch (Exception e) { log.debug("재고 이동 시드 스텝 스킵", e); }
    }
    private <T> T read(java.util.function.Supplier<T> query) { return tx.execute(status -> query.get()); }
}
