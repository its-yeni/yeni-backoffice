package com.yeni.backoffice.core.commerce.service;

import com.yeni.backoffice.core.commerce.dto.PurchaseOrderDtos.*;
import com.yeni.backoffice.core.commerce.entity.*;
import com.yeni.backoffice.core.commerce.enums.PurchaseOrderStatus;
import com.yeni.backoffice.core.commerce.repository.*;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.NotFoundException;
import com.yeni.backoffice.core.common.exception.ValidationBusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 발주(PO) → 발주 확정 → 입고 검수(GRN) 흐름.
 * 입고는 {@link InventoryLedgerService#receive} 를 통해 매장 재고·LOT·감사 로그를 한 번에 반영한다.
 */
@Service
public class PurchaseOrderService {

    private static final DateTimeFormatter PO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final List<PurchaseOrderStatus> OPEN = List.of(PurchaseOrderStatus.ORDERED, PurchaseOrderStatus.PARTIALLY_RECEIVED);

    private final PurchaseOrderRepository orders;
    private final PurchaseOrderItemRepository items;
    private final SupplierRepository suppliers;
    private final CommerceStoreRepository stores;
    private final ProductVariantRepository variants;
    private final ProductRepository products;
    private final InventoryLedgerService ledger;

    public PurchaseOrderService(PurchaseOrderRepository orders, PurchaseOrderItemRepository items,
            SupplierRepository suppliers, CommerceStoreRepository stores, ProductVariantRepository variants,
            ProductRepository products, InventoryLedgerService ledger) {
        this.orders = orders;
        this.items = items;
        this.suppliers = suppliers;
        this.stores = stores;
        this.variants = variants;
        this.products = products;
        this.ledger = ledger;
    }

    @Transactional
    public PoResponse create(PoCreateRequest request) {
        suppliers.findById(request.supplierId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "공급처를 찾을 수 없습니다."));
        stores.findById(request.storeId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "입고 매장을 찾을 수 없습니다."));
        if (request.items().isEmpty()) throw validation("발주 품목을 1개 이상 담아 주세요.");

        PurchaseOrder po = orders.save(PurchaseOrder.builder()
                .poNo(nextPoNo()).supplierId(request.supplierId()).storeId(request.storeId())
                .status(PurchaseOrderStatus.DRAFT).expectedArrivalDate(request.expectedArrivalDate())
                .totalAmount(BigDecimal.ZERO).memo(blankToNull(request.memo())).actor("ADMIN").build());
        BigDecimal total = BigDecimal.ZERO;
        for (PoItemRequest line : request.items()) {
            variants.findById(line.variantId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.PRODUCT_VARIANT_NOT_FOUND));
            PurchaseOrderItem item = items.save(PurchaseOrderItem.builder()
                    .purchaseOrderId(po.getId()).variantId(line.variantId())
                    .orderedQuantity(line.orderedQuantity()).receivedQuantity(0)
                    .unitCost(line.unitCost() == null ? BigDecimal.ZERO : line.unitCost()).build());
            total = total.add(item.lineAmount());
        }
        po.applyTotal(total);
        return detail(po.getId());
    }

    @Transactional
    public PoResponse updateHeader(Long id, Long supplierId, Long storeId, LocalDate expectedArrivalDate, String memo) {
        PurchaseOrder po = lock(id);
        po.updateHeader(supplierId, storeId, expectedArrivalDate, blankToNull(memo));
        return detail(id);
    }

    @Transactional
    public PoResponse place(Long id) {
        lock(id).place();
        return detail(id);
    }

    @Transactional
    public PoResponse cancel(Long id) {
        PurchaseOrder po = lock(id);
        if (items.findByPurchaseOrderIdOrderByIdAsc(id).stream().anyMatch(i -> i.getReceivedQuantity() > 0))
            throw validation("이미 일부 입고된 발주는 취소할 수 없습니다. 남은 수량만 조정하세요.");
        po.cancel();
        return detail(id);
    }

    /** 발주 대비 입고(GRN). 품목별 이번 입고 수량을 재고 원장에 반영하고 발주 상태를 재계산한다. */
    @Transactional
    public PoResponse receive(Long id, PoReceiveRequest request) {
        PurchaseOrder po = lock(id);
        if (!po.isReceivable())
            throw new ValidationBusinessException(ErrorCode.CONFLICT, "발주 확정(ORDERED) 또는 부분입고 상태에서만 입고할 수 있습니다.");
        Map<Long, PurchaseOrderItem> byId = items.findByPurchaseOrderIdOrderByIdAsc(id).stream()
                .collect(Collectors.toMap(PurchaseOrderItem::getId, Function.identity()));
        for (PoReceiveLine line : request.lines()) {
            PurchaseOrderItem item = byId.get(line.itemId());
            if (item == null) throw new NotFoundException(ErrorCode.NOT_FOUND, "발주 품목을 찾을 수 없습니다.");
            if (line.quantity() <= 0) continue;
            item.receive(line.quantity());
            ledger.receive(po.getStoreId(), item.getVariantId(), line.quantity(), item.getUnitCost(),
                    "LOT", line.manufacturedDate(), line.expirationDate(), blankToNull(line.memo()),
                    "발주 입고 " + po.getPoNo(), "PURCHASE_ORDER", po.getId(), "ADMIN");
        }
        List<PurchaseOrderItem> after = items.findByPurchaseOrderIdOrderByIdAsc(id);
        po.recalcReceiptStatus(after.stream().allMatch(PurchaseOrderItem::fullyReceived),
                after.stream().anyMatch(i -> i.getReceivedQuantity() > 0));
        return detail(id);
    }

    @Transactional(readOnly = true)
    public List<PoResponse> list(String status) {
        List<PurchaseOrder> rows = status == null || status.isBlank()
                ? orders.findAllByOrderByIdDesc()
                : orders.findByStatusInOrderByIdDesc(List.of(PurchaseOrderStatus.valueOf(status)));
        return rows.stream().map(po -> toResponse(po, items.findByPurchaseOrderIdOrderByIdAsc(po.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public PoResponse detail(Long id) {
        PurchaseOrder po = orders.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "발주를 찾을 수 없습니다."));
        return toResponse(po, items.findByPurchaseOrderIdOrderByIdAsc(id));
    }

    @Transactional(readOnly = true)
    public PoSummary summary() {
        List<PurchaseOrder> all = orders.findAllByOrderByIdDesc();
        LocalDate today = LocalDate.now();
        long draft = all.stream().filter(p -> p.getStatus() == PurchaseOrderStatus.DRAFT).count();
        long ordered = all.stream().filter(p -> p.getStatus() == PurchaseOrderStatus.ORDERED).count();
        long partial = all.stream().filter(p -> p.getStatus() == PurchaseOrderStatus.PARTIALLY_RECEIVED).count();
        long received = all.stream().filter(p -> p.getStatus() == PurchaseOrderStatus.RECEIVED).count();
        long overdue = all.stream().filter(p -> isOverdue(p, today)).count();
        BigDecimal openAmount = all.stream().filter(p -> OPEN.contains(p.getStatus()))
                .flatMap(p -> items.findByPurchaseOrderIdOrderByIdAsc(p.getId()).stream())
                .map(i -> i.getUnitCost().multiply(BigDecimal.valueOf(i.outstandingQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PoSummary(draft, ordered, partial, received, overdue, openAmount);
    }

    /** ORDERED/PARTIALLY_RECEIVED 발주의 매장·SKU별 미입고 수량 — "입고 예정" 계산용. key = "storeId:variantId". */
    @Transactional(readOnly = true)
    public Map<String, Integer> outstandingByStoreVariant() {
        Map<Long, Long> poStore = orders.findByStatusInOrderByIdDesc(OPEN).stream()
                .collect(Collectors.toMap(PurchaseOrder::getId, PurchaseOrder::getStoreId));
        Map<String, Integer> result = new HashMap<>();
        for (PurchaseOrderItem item : items.findOutstanding(OPEN)) {
            Long storeId = poStore.get(item.getPurchaseOrderId());
            if (storeId == null) continue;
            result.merge(storeId + ":" + item.getVariantId(), item.outstandingQuantity(), Integer::sum);
        }
        return result;
    }

    private PoResponse toResponse(PurchaseOrder po, List<PurchaseOrderItem> lines) {
        Map<Long, ProductVariant> variantMap = variants.findAllById(
                lines.stream().map(PurchaseOrderItem::getVariantId).toList()).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        Map<Long, String> productNames = products.findAllById(variantMap.values().stream()
                .map(ProductVariant::getProductId).distinct().toList()).stream()
                .collect(Collectors.toMap(Product::getId, Product::getProductName));
        List<PoItemResponse> itemResponses = lines.stream().map(i -> {
            ProductVariant v = variantMap.get(i.getVariantId());
            return new PoItemResponse(i.getId(), i.getVariantId(),
                    v == null ? "-" : productNames.getOrDefault(v.getProductId(), "-"),
                    v == null ? "-" : v.getSku(), v == null ? "-" : v.getOptionSummary(),
                    i.getOrderedQuantity(), i.getReceivedQuantity(), i.outstandingQuantity(),
                    i.getUnitCost(), i.lineAmount());
        }).toList();
        String supplierName = suppliers.findById(po.getSupplierId()).map(s -> s.getName()).orElse("-");
        String storeName = stores.findById(po.getStoreId()).map(CommerceStore::getStoreName).orElse("-");
        return new PoResponse(po.getId(), po.getPoNo(), po.getSupplierId(), supplierName, po.getStoreId(), storeName,
                po.getStatus().name(), po.getExpectedArrivalDate(), po.getOrderedAt(), isOverdue(po, LocalDate.now()),
                lines.stream().mapToInt(PurchaseOrderItem::getOrderedQuantity).sum(),
                lines.stream().mapToInt(PurchaseOrderItem::getReceivedQuantity).sum(),
                po.getTotalAmount(), po.getMemo(), po.getCreatedAt(), itemResponses);
    }

    private boolean isOverdue(PurchaseOrder po, LocalDate today) {
        return OPEN.contains(po.getStatus()) && po.getExpectedArrivalDate() != null
                && po.getExpectedArrivalDate().isBefore(today);
    }

    private PurchaseOrder lock(Long id) {
        return orders.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "발주를 찾을 수 없습니다."));
    }

    private String nextPoNo() {
        String base = "PO-" + LocalDate.now().format(PO_DATE) + "-";
        return base + String.format("%03d", orders.countByPoNoStartingWith(base) + 1);
    }
    private static String blankToNull(String v) { return v == null || v.isBlank() ? null : v.trim(); }
    private ValidationBusinessException validation(String message) {
        return new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
