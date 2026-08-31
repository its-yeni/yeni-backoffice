package com.yeni.backoffice.core.commerce.service;

import com.yeni.backoffice.core.commerce.dto.InventoryTransactionDtos.InventoryTransactionResponse;
import com.yeni.backoffice.core.commerce.entity.InventoryTransaction;
import com.yeni.backoffice.core.commerce.entity.Product;
import com.yeni.backoffice.core.commerce.entity.ProductVariant;
import com.yeni.backoffice.core.commerce.enums.InventoryTransactionType;
import com.yeni.backoffice.core.commerce.repository.InventoryTransactionRepository;
import com.yeni.backoffice.core.commerce.repository.ProductRepository;
import com.yeni.backoffice.core.commerce.repository.ProductVariantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * SKU 재고 변동 이력(InventoryTransaction)을 기록/조회하는 서비스.
 * 실제 재고 수량 증감(ProductVariant.increaseStock/decreaseStock)은 각 서비스가 이미 수행한 뒤,
 * 그 결과를 감사 로그로 남기는 목적으로만 사용한다 — 이 서비스 자체는 재고 수량을 바꾸지 않는다.
 */
@Service
public class InventoryTransactionService {
    private final InventoryTransactionRepository transactions;
    private final ProductVariantRepository variants;
    private final ProductRepository products;

    public InventoryTransactionService(InventoryTransactionRepository transactions, ProductVariantRepository variants, ProductRepository products) {
        this.transactions = transactions;
        this.variants = variants;
        this.products = products;
    }

    /**
     * 재고 변동 이력 한 건을 남긴다. stockAfter/reservedAfter는 variant의 "지금(호출 시점)" 값을 그대로 스냅샷으로 남기므로,
     * 반드시 variant의 재고 변경 메서드(receiveStock/reserve/releaseReservation/ship 등) 호출 뒤에 불러야 한다.
     */
    @Transactional
    public void record(ProductVariant variant, InventoryTransactionType type, int quantity,
            String reason, String referenceType, Long referenceId, String actor) {
        int stockBefore = variant.getStockQuantity();
        int reservedBefore = variant.getReservedQuantity();
        switch (type) {
            case RECEIPT, ADJUST_IN, TRANSFER_IN -> stockBefore -= quantity;
            case ADJUST_OUT, TRANSFER_OUT -> stockBefore += quantity;
            case RESERVE -> reservedBefore -= quantity;
            case RELEASE -> reservedBefore += quantity;
            case SHIPMENT -> {
                stockBefore += quantity;
                reservedBefore += quantity;
            }
        }
        transactions.save(InventoryTransaction.builder()
                .variantId(variant.getId()).sku(variant.getSku()).type(type).quantity(quantity)
                .stockBefore(stockBefore).stockAfter(variant.getStockQuantity())
                .reservedBefore(reservedBefore).reservedAfter(variant.getReservedQuantity()).reason(reason)
                .referenceType(referenceType).referenceId(referenceId).actor(actor).build());
    }

    @Transactional(readOnly = true)
    public List<InventoryTransactionResponse> listRecent() {
        return toResponses(transactions.findTop200ByOrderByIdDesc());
    }

    @Transactional(readOnly = true)
    public List<InventoryTransactionResponse> listRecentByType(InventoryTransactionType type) {
        return toResponses(transactions.findTop200ByTypeOrderByIdDesc(type));
    }

    @Transactional(readOnly = true)
    public List<InventoryTransactionResponse> listByVariant(Long variantId) {
        return toResponses(transactions.findByVariantIdOrderByIdDesc(variantId));
    }

    private List<InventoryTransactionResponse> toResponses(List<InventoryTransaction> rows) {
        if (rows.isEmpty()) return List.of();
        Set<Long> variantIds = rows.stream().map(InventoryTransaction::getVariantId).collect(Collectors.toSet());
        Map<Long, ProductVariant> variantMap = variants.findAllById(variantIds).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        Set<Long> productIds = variantMap.values().stream().map(ProductVariant::getProductId).collect(Collectors.toSet());
        Map<Long, Product> productMap = products.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        return rows.stream().map(t -> {
            ProductVariant v = variantMap.get(t.getVariantId());
            Product product = v == null ? null : productMap.get(v.getProductId());
            return InventoryTransactionResponse.from(t,
                    product == null ? "-" : product.getProductName(),
                    product == null ? "-" : product.getProductCode(),
                    product == null ? null : product.getStoreCode());
        }).toList();
    }
}
