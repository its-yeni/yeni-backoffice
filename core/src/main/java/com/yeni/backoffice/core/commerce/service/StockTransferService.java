package com.yeni.backoffice.core.commerce.service;

import com.yeni.backoffice.core.commerce.dto.StockTransferDtos.TransferCreateRequest;
import com.yeni.backoffice.core.commerce.dto.StockTransferDtos.TransferItemRequest;
import com.yeni.backoffice.core.commerce.dto.StockTransferDtos.TransferItemResponse;
import com.yeni.backoffice.core.commerce.dto.StockTransferDtos.TransferResponse;
import com.yeni.backoffice.core.commerce.entity.CommerceStore;
import com.yeni.backoffice.core.commerce.entity.ProductVariant;
import com.yeni.backoffice.core.commerce.entity.StockTransfer;
import com.yeni.backoffice.core.commerce.entity.StockTransferItem;
import com.yeni.backoffice.core.commerce.entity.StoreVariantInventory;
import com.yeni.backoffice.core.commerce.enums.StockTransferStatus;
import com.yeni.backoffice.core.commerce.repository.CommerceStoreRepository;
import com.yeni.backoffice.core.commerce.repository.ProductRepository;
import com.yeni.backoffice.core.commerce.repository.ProductVariantRepository;
import com.yeni.backoffice.core.commerce.repository.StockTransferItemRepository;
import com.yeni.backoffice.core.commerce.repository.StockTransferRepository;
import com.yeni.backoffice.core.commerce.repository.StoreVariantInventoryRepository;
import com.yeni.backoffice.core.common.exception.ConflictException;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.NotFoundException;
import com.yeni.backoffice.core.common.exception.ValidationBusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 매장(창고 포함) 간 재고 이동. REQUESTED(요청) → IN_TRANSIT(발송, 출발 매장 재고 차감) →
 * RECEIVED(입고, 도착 매장 재고 증가) 흐름을 관리한다. 재고 증감은 매장별 SKU 재고
 * {@link StoreVariantInventory} 에 대해 비관적 락을 걸고 처리한다.
 */
@Service
public class StockTransferService {

    private final StockTransferRepository transfers;
    private final StockTransferItemRepository items;
    private final StoreVariantInventoryRepository inventories;
    private final ProductVariantRepository variants;
    private final ProductRepository products;
    private final CommerceStoreRepository stores;
    private final InventoryLedgerService inventoryLedgerService;

    public StockTransferService(StockTransferRepository transfers, StockTransferItemRepository items,
            StoreVariantInventoryRepository inventories, ProductVariantRepository variants,
            ProductRepository products, CommerceStoreRepository stores, InventoryLedgerService inventoryLedgerService) {
        this.products = products;
        this.transfers = transfers;
        this.items = items;
        this.inventories = inventories;
        this.variants = variants;
        this.stores = stores;
        this.inventoryLedgerService = inventoryLedgerService;
    }

    @Transactional
    public TransferResponse create(TransferCreateRequest request) {
        if (request.sourceStoreId().equals(request.destinationStoreId())) {
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "출발지와 도착지는 달라야 합니다.");
        }
        store(request.sourceStoreId());
        store(request.destinationStoreId());

        StockTransfer transfer = transfers.save(StockTransfer.builder()
                .transferNo("TR-" + LocalDate.now().toString().replace("-", "") + "-"
                        + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .sourceStoreId(request.sourceStoreId())
                .destinationStoreId(request.destinationStoreId())
                .status(StockTransferStatus.REQUESTED)
                .reason(request.reason())
                .actor("ADMIN")
                .build());

        for (TransferItemRequest itemRequest : request.items()) {
            ProductVariant variant = variants.findById(itemRequest.variantId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.PRODUCT_VARIANT_NOT_FOUND));
            StoreVariantInventory source = inventories.findForUpdate(request.sourceStoreId(), itemRequest.variantId())
                    .orElseThrow(() -> new ValidationBusinessException(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH,
                            "출발 위치에 SKU 재고가 없습니다."));
            if (source.getAvailableQuantity() < itemRequest.quantity()) {
                throw new ValidationBusinessException(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH,
                        "이동 가능한 재고가 부족합니다: " + variant.getSku());
            }
            items.save(StockTransferItem.builder()
                    .transferId(transfer.getId())
                    .variantId(itemRequest.variantId())
                    .quantity(itemRequest.quantity())
                    .unitCost(source.getAverageUnitCost() == null ? BigDecimal.ZERO : source.getAverageUnitCost())
                    .build());
        }
        return response(transfer);
    }

    @Transactional
    public TransferResponse ship(Long id) {
        StockTransfer transfer = transfer(id);
        if (transfer.getStatus() != StockTransferStatus.REQUESTED) {
            throw conflict();
        }
        for (StockTransferItem item : items.findByTransferIdOrderById(id)) {
            var snapshot = inventoryLedgerService.transferOut(transfer.getSourceStoreId(), item.getVariantId(),
                    item.getQuantity(), transfer.getId(), "ADMIN");
            if (snapshot != null) {
                item.recordLotSnapshot(snapshot.lotNo(), snapshot.expirationDate());
                items.save(item);
            }
        }
        transfer.ship();
        return response(transfer);
    }

    @Transactional
    public TransferResponse receive(Long id) {
        StockTransfer transfer = transfer(id);
        if (transfer.getStatus() != StockTransferStatus.IN_TRANSIT) {
            throw conflict();
        }
        for (StockTransferItem item : items.findByTransferIdOrderById(id)) {
            inventoryLedgerService.transferIn(transfer.getDestinationStoreId(), item.getVariantId(), item.getQuantity(),
                    item.getUnitCost(), item.getLotNo(), null, item.getExpirationDate(), transfer.getId(), "ADMIN");
        }
        transfer.receive();
        return response(transfer);
    }

    @Transactional(readOnly = true)
    public List<TransferResponse> list() {
        return transfers.findAllByOrderByIdDesc().stream().map(this::response).toList();
    }

    private TransferResponse response(StockTransfer transfer) {
        List<StockTransferItem> transferItems = items.findByTransferIdOrderById(transfer.getId());
        Map<Long, ProductVariant> variantMap = variants.findAllById(
                        transferItems.stream().map(StockTransferItem::getVariantId).toList()).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        Map<Long, String> productNames = products.findAllById(
                        variantMap.values().stream().map(ProductVariant::getProductId).distinct().toList()).stream()
                .collect(Collectors.toMap(p -> p.getId(), p -> p.getProductName()));
        List<TransferItemResponse> rows = transferItems.stream()
                .map(item -> {
                    ProductVariant variant = variantMap.get(item.getVariantId());
                    return new TransferItemResponse(
                            item.getVariantId(),
                            variant == null ? "-" : productNames.getOrDefault(variant.getProductId(), "-"),
                            variant == null ? "-" : variant.getSku(),
                            variant == null ? "-" : variant.getOptionSummary(),
                            item.getQuantity(),
                            item.getUnitCost());
                })
                .toList();
        return new TransferResponse(
                transfer.getId(), transfer.getTransferNo(),
                transfer.getSourceStoreId(), store(transfer.getSourceStoreId()).getStoreName(),
                transfer.getDestinationStoreId(), store(transfer.getDestinationStoreId()).getStoreName(),
                transfer.getStatus().name(), transfer.getReason(),
                transfer.getShippedAt(), transfer.getReceivedAt(), rows, transfer.getCreatedAt());
    }

    private StockTransfer transfer(Long id) {
        return transfers.findById(id).orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND));
    }

    private CommerceStore store(Long id) {
        return stores.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "재고 위치를 찾을 수 없습니다."));
    }

    private ConflictException conflict() {
        return new ConflictException(ErrorCode.CONFLICT, "현재 상태에서는 처리할 수 없는 이동 전표입니다.");
    }
}
