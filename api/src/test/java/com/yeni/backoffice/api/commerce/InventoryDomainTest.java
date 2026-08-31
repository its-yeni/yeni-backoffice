package com.yeni.backoffice.api.commerce;

import com.yeni.backoffice.core.commerce.dto.PurchaseOrderDtos.*;
import com.yeni.backoffice.core.commerce.dto.StockCountDtos.CountLineResponse;
import com.yeni.backoffice.core.commerce.dto.StockCountDtos.StockCountResponse;
import com.yeni.backoffice.core.commerce.entity.*;
import com.yeni.backoffice.core.commerce.enums.ProductSaleStatus;
import com.yeni.backoffice.core.commerce.enums.StoreBusinessType;
import com.yeni.backoffice.core.commerce.repository.*;
import com.yeni.backoffice.core.commerce.service.InventoryLedgerService;
import com.yeni.backoffice.core.commerce.service.PurchaseOrderService;
import com.yeni.backoffice.core.commerce.service.StockCountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class InventoryDomainTest {

    @Autowired InventoryLedgerService ledger;
    @Autowired PurchaseOrderService purchaseOrders;
    @Autowired StockCountService stockCounts;
    @Autowired CommerceStoreRepository stores;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired StoreVariantInventoryRepository storeInventories;
    @Autowired InventoryLotRepository lots;
    @Autowired InventoryTransactionRepository transactions;
    @Autowired SupplierRepository suppliers;

    private CommerceStore newStore() {
        return stores.save(CommerceStore.builder()
                .storeCode("INV-" + UUID.randomUUID().toString().substring(0, 8)).storeName("재고 테스트 매장")
                .businessType(StoreBusinessType.ONLINE_RETAIL).brandName("테스트").description("재고 도메인 테스트")
                .active(true).build());
    }

    private Product newProduct() {
        return products.save(Product.builder()
                .productCode("P-" + UUID.randomUUID().toString().substring(0, 8)).productName("재고 테스트 상품")
                .category("테스트").salePrice(BigDecimal.valueOf(20000)).stockQuantity(0).inventoryManaged(true)
                .saleStatus(ProductSaleStatus.ON_SALE).storeCode("N/A").build());
    }

    private ProductVariant newVariant(Long productId) {
        return variants.save(ProductVariant.builder()
                .productId(productId).sku("SKU-" + UUID.randomUUID().toString().substring(0, 8))
                .combinationKey("BASE").optionSummary("기본").additionalPrice(BigDecimal.ZERO)
                .stockQuantity(0).reservedQuantity(0).safetyStock(5).saleStatus(ProductSaleStatus.SOLD_OUT).sortOrder(1).build());
    }

    @Test
    void ledgerReceiveKeepsVariantProjectionEqualToStoreSum() {
        CommerceStore store = newStore();
        Product product = newProduct();
        ProductVariant variant = newVariant(product.getId());

        ledger.receive(store.getId(), variant.getId(), 40, BigDecimal.valueOf(5000), "LOT",
                LocalDate.now(), null, null, "테스트 입고", "MANUAL", null, "TEST");

        ProductVariant reloaded = variants.findById(variant.getId()).orElseThrow();
        int storeSum = storeInventories.findByVariantId(variant.getId()).stream()
                .mapToInt(StoreVariantInventory::getStockQuantity).sum();
        assertThat(reloaded.getStockQuantity()).isEqualTo(40).isEqualTo(storeSum);
        assertThat(reloaded.getSaleStatus()).isEqualTo(ProductSaleStatus.ON_SALE);
        assertThat(lots.sumAvailableByStoreVariant().stream().anyMatch(r ->
                ((Number) r[1]).longValue() == variant.getId() && ((Number) r[2]).intValue() == 40)).isTrue();
        assertThat(transactions.findByVariantIdOrderByIdDesc(variant.getId()).get(0).getType().name()).isEqualTo("RECEIPT");
    }

    @Test
    void purchaseOrderFlowFromDraftToReceived() {
        CommerceStore store = newStore();
        Product product = newProduct();
        ProductVariant variant = newVariant(product.getId());
        Supplier supplier = suppliers.save(Supplier.builder().supplierCode("S-" + UUID.randomUUID().toString().substring(0, 6))
                .name("테스트 공급처").leadTimeDays(7).active(true).build());

        PoResponse po = purchaseOrders.create(new PoCreateRequest(supplier.getId(), store.getId(),
                LocalDate.now().plusDays(7), "테스트 발주",
                List.of(new PoItemRequest(variant.getId(), 30, BigDecimal.valueOf(4000)))));
        assertThat(po.status()).isEqualTo("DRAFT");

        purchaseOrders.place(po.id());
        Long itemId = purchaseOrders.detail(po.id()).items().get(0).id();

        PoResponse partial = purchaseOrders.receive(po.id(),
                new PoReceiveRequest(List.of(new PoReceiveLine(itemId, 10, LocalDate.now(), null, null))));
        assertThat(partial.status()).isEqualTo("PARTIALLY_RECEIVED");
        assertThat(variants.findById(variant.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);

        PoResponse done = purchaseOrders.receive(po.id(),
                new PoReceiveRequest(List.of(new PoReceiveLine(itemId, 20, LocalDate.now(), null, null))));
        assertThat(done.status()).isEqualTo("RECEIVED");
        assertThat(variants.findById(variant.getId()).orElseThrow().getStockQuantity()).isEqualTo(30);

        assertThatThrownBy(() -> purchaseOrders.receive(po.id(),
                new PoReceiveRequest(List.of(new PoReceiveLine(itemId, 5, null, null, null)))))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void stockCountCompletePostsAdjustment() {
        CommerceStore store = newStore();
        Product product = newProduct();
        ProductVariant variant = newVariant(product.getId());
        ledger.receive(store.getId(), variant.getId(), 20, BigDecimal.valueOf(3000), "LOT",
                LocalDate.now(), null, null, "초기", "MANUAL", null, "TEST");

        StockCountResponse count = stockCounts.start(store.getId(), "ALL", "테스트 실사");
        CountLineResponse line = count.lines().stream().filter(l -> l.variantId().equals(variant.getId())).findFirst().orElseThrow();
        stockCounts.enterCount(count.id(), line.id(), 17); // 실물 3개 부족
        StockCountResponse completed = stockCounts.complete(count.id());

        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(variants.findById(variant.getId()).orElseThrow().getStockQuantity()).isEqualTo(17);
        assertThat(transactions.findByVariantIdOrderByIdDesc(variant.getId()).stream()
                .anyMatch(t -> "STOCK_COUNT".equals(t.getReferenceType()) && t.getType().name().equals("ADJUST_OUT"))).isTrue();
    }
}
