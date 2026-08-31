package com.yeni.backoffice.core.commerce.service;

import com.yeni.backoffice.core.commerce.entity.InventoryLot;
import com.yeni.backoffice.core.commerce.entity.ProductVariant;
import com.yeni.backoffice.core.commerce.entity.StoreVariantInventory;
import com.yeni.backoffice.core.commerce.enums.InventoryTransactionType;
import com.yeni.backoffice.core.commerce.enums.ProductSaleStatus;
import com.yeni.backoffice.core.commerce.repository.InventoryLotRepository;
import com.yeni.backoffice.core.commerce.repository.ProductVariantRepository;
import com.yeni.backoffice.core.commerce.repository.StoreVariantInventoryRepository;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.NotFoundException;
import com.yeni.backoffice.core.common.exception.ValidationBusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * SKU 재고 증감의 단일 쓰기 경로.
 * <p>
 * 모든 입고/예약/예약해제/출고/조정은 매장별 재고({@link StoreVariantInventory})와 LOT({@link InventoryLot})에
 * 먼저 반영하고, 그 직후 해당 SKU의 전역 프로젝션({@link ProductVariant#applyProjection})을 매장 행 합계로
 * 다시 맞춘다. 이렇게 하면 전역 현재재고·예약재고가 항상 {@code Σ 매장 재고} 와 일치한다.
 * <p>
 * 재고 변동 감사 로그는 프로젝션 동기화 뒤에 {@link InventoryTransactionService#record}로 남긴다 —
 * 한 호출은 매장 한 곳만 건드리므로 전역 프로젝션의 변화량이 곧 이번 변동량과 같고, record가 역산하는
 * before 값도 그대로 정확하다.
 */
@Service
public class InventoryLedgerService {

    private static final DateTimeFormatter LOT_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final StoreVariantInventoryRepository storeInventories;
    private final ProductVariantRepository variants;
    private final InventoryLotRepository lots;
    private final InventoryTransactionService transactions;

    public InventoryLedgerService(StoreVariantInventoryRepository storeInventories, ProductVariantRepository variants,
            InventoryLotRepository lots, InventoryTransactionService transactions) {
        this.storeInventories = storeInventories;
        this.variants = variants;
        this.lots = lots;
        this.transactions = transactions;
    }

    /** 입고: 매장 재고 증가 + LOT 발번/누적 + 프로젝션 동기화. */
    @Transactional
    public String receive(Long storeId, Long variantId, int quantity, BigDecimal unitCost, String lotPrefix,
            LocalDate manufacturedDate, LocalDate expirationDate, String lotMemo,
            String reason, String referenceType, Long referenceId, String actor) {
        if (quantity <= 0) throw validation("입고 수량은 1개 이상이어야 합니다.");
        if (expirationDate != null && manufacturedDate != null && manufacturedDate.isAfter(expirationDate))
            throw validation("제조일은 유통기한보다 늦을 수 없습니다.");
        ProductVariant variant = lockVariant(variantId);
        StoreVariantInventory inventory = storeInventories.findForUpdate(storeId, variantId)
                .orElseGet(() -> storeInventories.save(newInventory(storeId, variant)));
        inventory.receive(quantity, unitCost == null ? BigDecimal.ZERO : unitCost);
        String lotNo = upsertLot(storeId, variantId, quantity, lotPrefix, manufacturedDate, expirationDate, lotMemo);
        syncVariant(variant);
        transactions.record(variant, InventoryTransactionType.RECEIPT, quantity, withLot(reason, lotNo), referenceType, referenceId, actor);
        return lotNo;
    }

    /** 입고(수동 LOT 번호 지정). 지정 번호가 비어 있으면 자동 발번한다. */
    @Transactional
    public String receiveWithLot(Long storeId, Long variantId, int quantity, BigDecimal unitCost, String lotNo,
            LocalDate manufacturedDate, LocalDate expirationDate, String lotMemo,
            String reason, String referenceType, Long referenceId, String actor) {
        if (lotNo == null || lotNo.isBlank()) {
            return receive(storeId, variantId, quantity, unitCost, "LOT", manufacturedDate, expirationDate, lotMemo,
                    reason, referenceType, referenceId, actor);
        }
        if (quantity <= 0) throw validation("입고 수량은 1개 이상이어야 합니다.");
        if (expirationDate != null && manufacturedDate != null && manufacturedDate.isAfter(expirationDate))
            throw validation("제조일은 유통기한보다 늦을 수 없습니다.");
        ProductVariant variant = lockVariant(variantId);
        StoreVariantInventory inventory = storeInventories.findForUpdate(storeId, variantId)
                .orElseGet(() -> storeInventories.save(newInventory(storeId, variant)));
        inventory.receive(quantity, unitCost == null ? BigDecimal.ZERO : unitCost);
        appendLot(storeId, variantId, lotNo.trim(), quantity, manufacturedDate, expirationDate, lotMemo);
        syncVariant(variant);
        transactions.record(variant, InventoryTransactionType.RECEIPT, quantity, withLot(reason, lotNo.trim()), referenceType, referenceId, actor);
        return lotNo.trim();
    }

    /** 주문 생성: 매장 가용재고 범위에서 예약재고만 늘린다. */
    @Transactional
    public void reserve(Long storeId, Long variantId, int quantity, String reason, String referenceType, Long referenceId, String actor) {
        ProductVariant variant = lockVariant(variantId);
        if (variant.getSaleStatus() != ProductSaleStatus.ON_SALE)
            throw new ValidationBusinessException(ErrorCode.PRODUCT_NOT_ON_SALE, "판매 중이 아닌 옵션 조합은 주문할 수 없습니다.");
        StoreVariantInventory inventory = storeInventories.findForUpdate(storeId, variantId)
                .orElseGet(() -> storeInventories.save(newInventory(storeId, variant)));
        inventory.reserve(quantity);
        syncVariant(variant);
        transactions.record(variant, InventoryTransactionType.RESERVE, quantity, reason, referenceType, referenceId, actor);
    }

    /** 결제 실패/취소: 예약재고만 되돌린다. */
    @Transactional
    public void release(Long storeId, Long variantId, int quantity, String reason, String referenceType, Long referenceId, String actor) {
        ProductVariant variant = lockVariant(variantId);
        StoreVariantInventory inventory = storeInventories.findForUpdate(storeId, variantId).orElse(null);
        if (inventory == null) return;
        inventory.release(quantity);
        syncVariant(variant);
        transactions.record(variant, InventoryTransactionType.RELEASE, quantity, reason, referenceType, referenceId, actor);
    }

    /** 출고 완료: 매장 현재·예약재고를 함께 줄이고 FEFO로 LOT를 차감한다. 매출원가(COGS)를 반환한다. */
    @Transactional
    public BigDecimal ship(Long storeId, Long variantId, int quantity, String reason, String referenceType, Long referenceId, String actor) {
        ProductVariant variant = lockVariant(variantId);
        StoreVariantInventory inventory = storeInventories.findForUpdate(storeId, variantId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "출고 위치의 SKU 재고를 찾을 수 없습니다."));
        BigDecimal cogs = inventory.ship(quantity);
        consumeLotsFefo(storeId, variantId, quantity);
        syncVariant(variant);
        transactions.record(variant, InventoryTransactionType.SHIPMENT, quantity, reason, referenceType, referenceId, actor);
        return cogs;
    }

    /** 재고 조정(실사·수기). delta > 0 이면 조정 LOT 신규, delta < 0 이면 FEFO 차감. */
    @Transactional
    public void adjust(Long storeId, Long variantId, int delta, String reason, String referenceType, Long referenceId, String actor) {
        if (delta == 0) return;
        ProductVariant variant = lockVariant(variantId);
        StoreVariantInventory inventory = storeInventories.findForUpdate(storeId, variantId)
                .orElseGet(() -> storeInventories.save(newInventory(storeId, variant)));
        inventory.adjustStock(delta);
        String auditReason = reason;
        if (delta > 0) {
            auditReason = withLot(reason, upsertLot(storeId, variantId, delta, "ADJ", null, null, reason));
        } else {
            consumeLotsFefo(storeId, variantId, -delta);
        }
        syncVariant(variant);
        transactions.record(variant, delta > 0 ? InventoryTransactionType.ADJUST_IN : InventoryTransactionType.ADJUST_OUT,
                Math.abs(delta), auditReason, referenceType, referenceId, actor);
    }

    /**
     * 매장 이동 출고: 출발 매장 현재재고 차감 + FEFO LOT 차감. 소비한 LOT 중 가장 임박한 1건의
     * (lotNo, 제조일, 유통기한)을 스냅샷으로 돌려줘 도착 매장 입고 시 유통기한 추적을 이어가게 한다.
     */
    @Transactional
    public LotSnapshot transferOut(Long storeId, Long variantId, int quantity, Long transferId, String actor) {
        ProductVariant variant = lockVariant(variantId);
        StoreVariantInventory inventory = storeInventories.findForUpdate(storeId, variantId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "출발 매장의 SKU 재고를 찾을 수 없습니다."));
        inventory.transferOut(quantity);
        LotSnapshot snapshot = null;
        int remaining = quantity;
        for (InventoryLot lot : lots.findAvailableForFefo(storeId, variantId)) {
            if (snapshot == null) snapshot = new LotSnapshot(lot.getLotNo(), lot.getManufacturedDate(), lot.getExpirationDate());
            remaining -= lot.consumeUpTo(remaining);
            lots.save(lot);
            if (remaining <= 0) break;
        }
        syncVariant(variant);
        transactions.record(variant, InventoryTransactionType.TRANSFER_OUT, quantity, "매장 이동 출고", "TRANSFER", transferId, actor);
        return snapshot;
    }

    /** 매장 이동 입고: 도착 매장 현재재고 증가 + 출발 매장에서 넘어온 LOT 정보로 LOT 생성. */
    @Transactional
    public void transferIn(Long storeId, Long variantId, int quantity, BigDecimal unitCost,
            String sourceLotNo, LocalDate manufacturedDate, LocalDate expirationDate, Long transferId, String actor) {
        ProductVariant variant = lockVariant(variantId);
        StoreVariantInventory inventory = storeInventories.findForUpdate(storeId, variantId)
                .orElseGet(() -> storeInventories.save(newInventory(storeId, variant)));
        inventory.transferIn(quantity, unitCost == null ? BigDecimal.ZERO : unitCost);
        String lotNo = sourceLotNo == null || sourceLotNo.isBlank() ? nextLotNo("TR") : sourceLotNo.trim() + "-T";
        appendLot(storeId, variantId, lotNo, quantity, manufacturedDate, expirationDate, "매장 이동 입고");
        syncVariant(variant);
        transactions.record(variant, InventoryTransactionType.TRANSFER_IN, quantity,
                "매장 이동 입고 · LOT " + lotNo, "TRANSFER", transferId, actor);
    }

    public record LotSnapshot(String lotNo, LocalDate manufacturedDate, LocalDate expirationDate) {}

    /** 매장 재고 행 합계를 전역 프로젝션에 반영. 매장 행이 없으면(폴백 경로) 건드리지 않는다. */
    private void syncVariant(ProductVariant variant) {
        List<StoreVariantInventory> rows = storeInventories.findByVariantId(variant.getId());
        if (rows.isEmpty()) return;
        int stock = rows.stream().mapToInt(StoreVariantInventory::getStockQuantity).sum();
        int reserved = rows.stream().mapToInt(StoreVariantInventory::getReservedQuantity).sum();
        variant.applyProjection(stock, reserved);
        variants.save(variant);
    }

    /** 시드/보정용 — 매장 재고 변경 없이 전역 프로젝션만 다시 맞춘다. */
    @Transactional
    public void resyncVariant(Long variantId) {
        variants.findById(variantId).ifPresent(this::syncVariant);
    }

    private String upsertLot(Long storeId, Long variantId, int quantity, String prefix,
            LocalDate manufacturedDate, LocalDate expirationDate, String memo) {
        String lotNo = nextLotNo(prefix == null || prefix.isBlank() ? "LOT" : prefix);
        appendLot(storeId, variantId, lotNo, quantity, manufacturedDate, expirationDate, memo);
        return lotNo;
    }

    private void appendLot(Long storeId, Long variantId, String lotNo, int quantity,
            LocalDate manufacturedDate, LocalDate expirationDate, String memo) {
        InventoryLot lot = lots.findByStoreIdAndVariantIdAndLotNo(storeId, variantId, lotNo)
                .orElseGet(() -> InventoryLot.builder()
                        .storeId(storeId).variantId(variantId).lotNo(lotNo)
                        .manufacturedDate(manufacturedDate).expirationDate(expirationDate)
                        .receivedQuantity(0).availableQuantity(0).memo(memo).build());
        lot.receive(quantity, manufacturedDate, expirationDate, memo);
        lots.save(lot);
    }

    private void consumeLotsFefo(Long storeId, Long variantId, int quantity) {
        int remaining = quantity;
        for (InventoryLot lot : lots.findAvailableForFefo(storeId, variantId)) {
            remaining -= lot.consumeUpTo(remaining);
            lots.save(lot);
            if (remaining <= 0) break;
        }
    }

    private String withLot(String reason, String lotNo) {
        String base = reason == null || reason.isBlank() ? "입고" : reason;
        return lotNo == null ? base : base + " · LOT " + lotNo;
    }

    private String nextLotNo(String prefix) {
        String base = prefix + "-" + LocalDate.now().format(LOT_DATE) + "-";
        return base + String.format("%03d", lots.countByLotNoStartingWith(base) + 1);
    }

    private ProductVariant lockVariant(Long variantId) {
        return variants.findByIdForUpdate(variantId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PRODUCT_VARIANT_NOT_FOUND));
    }

    private StoreVariantInventory newInventory(Long storeId, ProductVariant variant) {
        // 이 SKU의 첫 매장 재고 행이면, 그동안 SKU 전역 수량으로만 관리되던 기존 재고를 이 매장으로 귀속시킨다
        // (단일 매장 가정). 두 번째 이후 매장 행은 0에서 시작한다. 이렇게 해야 전역 프로젝션이 유지된다.
        boolean firstRow = storeInventories.findByVariantId(variant.getId()).isEmpty();
        return StoreVariantInventory.builder()
                .storeId(storeId).variantId(variant.getId())
                .stockQuantity(firstRow ? variant.getStockQuantity() : 0)
                .reservedQuantity(firstRow ? variant.getReservedQuantity() : 0)
                .averageUnitCost(BigDecimal.ZERO)
                .safetyStock(variant.getSafetyStock())
                .saleEnabled(true)
                .build();
    }

    private ValidationBusinessException validation(String message) {
        return new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
