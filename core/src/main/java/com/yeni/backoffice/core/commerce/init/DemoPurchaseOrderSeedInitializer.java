package com.yeni.backoffice.core.commerce.init;

import com.yeni.backoffice.core.commerce.dto.PurchaseOrderDtos.*;
import com.yeni.backoffice.core.commerce.entity.CommerceStore;
import com.yeni.backoffice.core.commerce.entity.ProductVariant;
import com.yeni.backoffice.core.commerce.entity.Supplier;
import com.yeni.backoffice.core.commerce.enums.ProductSaleStatus;
import com.yeni.backoffice.core.commerce.repository.CommerceStoreRepository;
import com.yeni.backoffice.core.commerce.repository.ProductVariantRepository;
import com.yeni.backoffice.core.commerce.repository.PurchaseOrderRepository;
import com.yeni.backoffice.core.commerce.repository.SupplierRepository;
import com.yeni.backoffice.core.commerce.service.PurchaseOrderService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
import java.util.ArrayList;
import java.util.List;

/**
 * 데모 발주 데이터. 상태 분포(작성 중/발주 완료/부분 입고/입고 완료/취소)와 예정일 초과 건을 만들어
 * 발주 관리·발주 제안·재고 현황(입고 예정)이 실제 운영처럼 보이게 한다.
 * 입고 처리는 {@link PurchaseOrderService#receive} 를 그대로 타므로 LOT·매장 재고·입출고 이력이 함께 쌓인다.
 */
@Component
@Profile("fly | demo")
@Order(186)
public class DemoPurchaseOrderSeedInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoPurchaseOrderSeedInitializer.class);

    @PersistenceContext private EntityManager em;
    private final TransactionTemplate tx;
    private final PurchaseOrderService purchaseOrders;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository suppliers;
    private final ProductVariantRepository variants;
    private final com.yeni.backoffice.core.commerce.repository.ProductRepository products;
    private final CommerceStoreRepository stores;

    public DemoPurchaseOrderSeedInitializer(PlatformTransactionManager txManager, PurchaseOrderService purchaseOrders,
            PurchaseOrderRepository purchaseOrderRepository, SupplierRepository suppliers,
            ProductVariantRepository variants,
            com.yeni.backoffice.core.commerce.repository.ProductRepository products, CommerceStoreRepository stores) {
        this.tx = new TransactionTemplate(txManager);
        this.purchaseOrders = purchaseOrders;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.suppliers = suppliers;
        this.variants = variants;
        this.products = products;
        this.stores = stores;
    }

    @Override
    public void run(String... args) {
        try { seed(); } catch (Exception e) { log.warn("발주 시드 스킵", e); }
    }

    private void seed() {
        if (read(() -> purchaseOrderRepository.count()) > 0L) return;
        List<Long> supplierIds = read(() -> suppliers.findAll().stream().map(Supplier::getId).toList());
        // 발주는 그 매장의 상품 카탈로그(product.store_code 일치) SKU만 담는다 — 발주 입고가 다른 매장 재고를 만들면 안 됨.
        java.util.Map<Long, List<Long>> variantsByStore = read(() -> {
            java.util.Map<Long, List<Long>> map = new java.util.LinkedHashMap<>();
            for (CommerceStore store : stores.findAllByOrderByIdAsc()) {
                java.util.Set<Long> productIds = products.findByStoreCode(store.getStoreCode()).stream()
                        .map(com.yeni.backoffice.core.commerce.entity.Product::getId).collect(java.util.stream.Collectors.toSet());
                List<Long> vs = variants.findAllByOrderByProductIdAscSortOrderAscIdAsc().stream()
                        .filter(v -> productIds.contains(v.getProductId()))
                        .filter(v -> v.getSaleStatus() != ProductSaleStatus.STOPPED)
                        .map(ProductVariant::getId).toList();
                if (!vs.isEmpty()) map.put(store.getId(), vs);
            }
            return map;
        });
        List<Long> storeIds = new ArrayList<>(variantsByStore.keySet());
        if (supplierIds.isEmpty() || storeIds.isEmpty()) return;

        LocalDate today = LocalDate.now();
        // {상태코드, 예정일 오프셋(일), 품목수, 입고비율(%)}  상태코드: D=DRAFT O=ORDERED P=부분입고 R=입고완료 C=취소 X=예정일초과
        String[][] plan = {
                {"D", "10", "3", "0"}, {"D", "7", "2", "0"}, {"D", "14", "4", "0"},
                {"O", "9", "2", "0"}, {"O", "12", "3", "0"}, {"O", "6", "2", "0"},
                {"O", "18", "3", "0"}, {"O", "4", "2", "0"}, {"O", "15", "2", "0"},
                {"X", "-3", "2", "0"}, {"X", "-6", "3", "0"},
                {"P", "-1", "3", "40"}, {"P", "-4", "2", "50"}, {"P", "-2", "4", "30"}, {"P", "-8", "2", "60"},
                {"R", "-10", "2", "100"}, {"R", "-13", "3", "100"}, {"R", "-7", "2", "100"}, {"R", "-16", "2", "100"},
                {"R", "-20", "3", "100"}, {"R", "-9", "2", "100"}, {"R", "-24", "2", "100"}, {"R", "-12", "3", "100"},
                {"C", "5", "2", "0"},
        };
        for (int i = 0; i < plan.length; i++) {
            String code = plan[i][0];
            LocalDate eta = today.plusDays(Long.parseLong(plan[i][1]));
            int receiptPct = Integer.parseInt(plan[i][3]);
            Long supplierId = supplierIds.get(i % supplierIds.size());
            Long storeId = storeIds.get(i % storeIds.size());
            List<Long> storeVariants = variantsByStore.get(storeId);
            int itemCount = Math.min(Integer.parseInt(plan[i][2]), storeVariants.size());
            List<PoItemRequest> items = new ArrayList<>();
            for (int k = 0; k < itemCount; k++) {
                Long variantId = storeVariants.get((i * 3 + k) % storeVariants.size());
                if (items.stream().anyMatch(it -> it.variantId().equals(variantId))) continue;
                int qty = 20 + (k * 15) + (i % 3) * 10;
                BigDecimal cost = BigDecimal.valueOf(4200L + (long) (variantId % 7) * 900);
                items.add(new PoItemRequest(variantId, qty, cost));
            }
            int step = i;
            step(() -> {
                PoResponse po = purchaseOrders.create(new PoCreateRequest(supplierId, storeId, eta,
                        "데모 발주 #" + (step + 1), items));
                if (code.equals("D")) return;
                purchaseOrders.place(po.id());
                if (code.equals("O") || code.equals("X")) return;
                if (code.equals("C")) { cancelViaSql(po.id()); return; }
                PoResponse detail = purchaseOrders.detail(po.id());
                List<PoReceiveLine> lines = new ArrayList<>();
                for (PoItemResponse item : detail.items()) {
                    int recv = code.equals("R") ? item.orderedQuantity()
                            : Math.max(1, item.orderedQuantity() * receiptPct / 100);
                    LocalDate exp = (item.variantId() % 4 == 0) ? today.plusDays(60 + (item.variantId() % 5) * 30) : null;
                    lines.add(new PoReceiveLine(item.id(), Math.min(recv, item.orderedQuantity()),
                            today.minusDays(30), exp, null));
                }
                purchaseOrders.receive(po.id(), new PoReceiveRequest(lines));
            });
        }
        log.info("데모 발주 데이터 시딩 완료");
    }

    /** 취소는 서비스 규칙상 미입고 상태에서만 가능 — DRAFT/ORDERED 건이므로 서비스 경유로 취소한다. */
    private void cancelViaSql(Long poId) {
        try { purchaseOrders.cancel(poId); } catch (Exception e) { log.debug("발주 취소 시드 스킵 poId={}", poId, e); }
    }

    private void step(Runnable action) {
        try { tx.executeWithoutResult(status -> action.run()); }
        catch (Exception e) { log.debug("발주 시드 스텝 스킵", e); }
    }
    private <T> T read(java.util.function.Supplier<T> query) { return tx.execute(status -> query.get()); }
}
