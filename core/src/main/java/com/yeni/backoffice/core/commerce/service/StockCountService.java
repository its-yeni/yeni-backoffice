package com.yeni.backoffice.core.commerce.service;

import com.yeni.backoffice.core.commerce.dto.StockCountDtos.*;
import com.yeni.backoffice.core.commerce.entity.*;
import com.yeni.backoffice.core.commerce.enums.StockCountStatus;
import com.yeni.backoffice.core.commerce.repository.*;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.NotFoundException;
import com.yeni.backoffice.core.common.exception.ValidationBusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 재고 실사(cycle count): 실사 시작 시 시스템 재고를 라인으로 스냅샷 → 라인별 카운트 입력 →
 * 반영(완료) 시 카운트와 시스템 재고의 차이만큼 {@link InventoryLedgerService#adjust} 로 조정 전기한다.
 */
@Service
public class StockCountService {

    private static final DateTimeFormatter SC_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StockCountRepository counts;
    private final StockCountLineRepository lines;
    private final StoreVariantInventoryRepository storeInventories;
    private final ProductVariantRepository variants;
    private final ProductRepository products;
    private final CommerceStoreRepository stores;
    private final InventoryLedgerService ledger;

    public StockCountService(StockCountRepository counts, StockCountLineRepository lines,
            StoreVariantInventoryRepository storeInventories, ProductVariantRepository variants,
            ProductRepository products, CommerceStoreRepository stores, InventoryLedgerService ledger) {
        this.counts = counts;
        this.lines = lines;
        this.storeInventories = storeInventories;
        this.variants = variants;
        this.products = products;
        this.stores = stores;
        this.ledger = ledger;
    }

    @Transactional
    public StockCountResponse start(Long storeId, String scope, String memo) {
        stores.findById(storeId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "실사 매장을 찾을 수 없습니다."));
        List<StoreVariantInventory> rows = storeInventories.findByStoreId(storeId);
        if (rows.isEmpty()) throw validation("이 매장에는 실사할 SKU 재고가 없습니다.");

        Map<Long, ProductVariant> variantMap = variants.findAllById(
                rows.stream().map(StoreVariantInventory::getVariantId).toList()).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        Map<Long, Product> productMap = products.findAllById(variantMap.values().stream()
                .map(ProductVariant::getProductId).distinct().toList()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        String normalizedScope = scope == null || scope.isBlank() ? "ALL" : scope.trim();
        List<StoreVariantInventory> target = rows.stream()
                .filter(inv -> {
                    ProductVariant v = variantMap.get(inv.getVariantId());
                    Product p = v == null ? null : productMap.get(v.getProductId());
                    return matchesScope(normalizedScope, inv, v, p);
                })
                .toList();
        if (target.isEmpty()) throw validation("실사 범위에 해당하는 SKU가 없습니다.");

        StockCount count = counts.save(StockCount.builder()
                .countNo(nextCountNo()).storeId(storeId).status(StockCountStatus.IN_PROGRESS)
                .memo(blankToNull(memo)).actor("ADMIN").build());
        for (StoreVariantInventory inv : target) {
            lines.save(StockCountLine.builder()
                    .stockCountId(count.getId()).variantId(inv.getVariantId())
                    .systemQuantity(inv.getStockQuantity()).countedQuantity(null).differenceQuantity(0).build());
        }
        return detail(count.getId());
    }

    @Transactional
    public StockCountResponse enterCount(Long countId, Long lineId, int countedQuantity) {
        StockCount count = lock(countId);
        if (count.getStatus() != StockCountStatus.IN_PROGRESS)
            throw new ValidationBusinessException(ErrorCode.CONFLICT, "실사 중 상태에서만 카운트를 입력할 수 있습니다.");
        StockCountLine line = lines.findByIdAndStockCountId(lineId, countId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "실사 라인을 찾을 수 없습니다."));
        line.enterCount(countedQuantity);
        return detail(countId);
    }

    /** 반영(완료): 카운트가 입력된 라인의 차이만큼 재고를 조정한다. 미입력 라인은 차이 없음으로 넘어간다. */
    @Transactional
    public StockCountResponse complete(Long countId) {
        StockCount count = lock(countId);
        for (StockCountLine line : lines.findByStockCountIdOrderByIdAsc(countId)) {
            if (!line.counted() || line.getDifferenceQuantity() == 0) continue;
            ledger.adjust(count.getStoreId(), line.getVariantId(), line.getDifferenceQuantity(),
                    "재고 실사 " + count.getCountNo(), "STOCK_COUNT", count.getId(), "ADMIN");
        }
        count.complete();
        return detail(countId);
    }

    @Transactional
    public StockCountResponse cancel(Long countId) {
        lock(countId).cancel();
        return detail(countId);
    }

    @Transactional(readOnly = true)
    public List<StockCountResponse> list() {
        return counts.findAllByOrderByIdDesc().stream()
                .map(c -> toResponse(c, lines.findByStockCountIdOrderByIdAsc(c.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public StockCountResponse detail(Long countId) {
        StockCount count = counts.findById(countId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "실사를 찾을 수 없습니다."));
        return toResponse(count, lines.findByStockCountIdOrderByIdAsc(countId));
    }

    private boolean matchesScope(String scope, StoreVariantInventory inv, ProductVariant variant, Product product) {
        if (scope.equalsIgnoreCase("ALL")) return true;
        if (scope.equalsIgnoreCase("LOW_STOCK"))
            return inv.getAvailableQuantity() <= Math.max(inv.getSafetyStock(),
                    variant == null ? 0 : variant.getSafetyStock());
        if (scope.toUpperCase().startsWith("CATEGORY:")) {
            String category = scope.substring("CATEGORY:".length()).trim();
            return product != null && category.equalsIgnoreCase(product.getCategory());
        }
        return true;
    }

    private StockCountResponse toResponse(StockCount count, List<StockCountLine> countLines) {
        Map<Long, ProductVariant> variantMap = variants.findAllById(
                countLines.stream().map(StockCountLine::getVariantId).toList()).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        Map<Long, Product> productMap = products.findAllById(variantMap.values().stream()
                .map(ProductVariant::getProductId).distinct().toList()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<CountLineResponse> lineResponses = countLines.stream().map(l -> {
            ProductVariant v = variantMap.get(l.getVariantId());
            Product p = v == null ? null : productMap.get(v.getProductId());
            return new CountLineResponse(l.getId(), l.getVariantId(),
                    p == null ? "-" : p.getProductName(), v == null ? "-" : v.getSku(),
                    v == null ? "-" : v.getOptionSummary(), p == null ? null : p.getCategory(),
                    l.getSystemQuantity(), l.getCountedQuantity(), l.getDifferenceQuantity(), l.counted());
        }).toList();
        String storeName = stores.findById(count.getStoreId()).map(CommerceStore::getStoreName).orElse("-");
        int diffCount = (int) countLines.stream().filter(l -> l.counted() && l.getDifferenceQuantity() != 0).count();
        int netDiff = countLines.stream().filter(StockCountLine::counted).mapToInt(StockCountLine::getDifferenceQuantity).sum();
        return new StockCountResponse(count.getId(), count.getCountNo(), count.getStoreId(), storeName,
                count.getStatus().name(), count.getMemo(), count.getCompletedAt(), count.getCreatedAt(),
                countLines.size(), (int) countLines.stream().filter(StockCountLine::counted).count(),
                diffCount, netDiff, lineResponses);
    }

    private StockCount lock(Long id) {
        return counts.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "실사를 찾을 수 없습니다."));
    }
    private String nextCountNo() {
        String base = "SC-" + LocalDate.now().format(SC_DATE) + "-";
        return base + String.format("%02d", counts.countByCountNoStartingWith(base) + 1);
    }
    private static String blankToNull(String v) { return v == null || v.isBlank() ? null : v.trim(); }
    private ValidationBusinessException validation(String message) {
        return new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
