package com.yeni.backoffice.core.commerce.dto;

import com.yeni.backoffice.core.commerce.entity.InventoryTransaction;
import java.time.LocalDateTime;

public final class InventoryTransactionDtos {
    private InventoryTransactionDtos() {}

    public record InventoryTransactionResponse(Long id, Long variantId, String sku, String productName,
            String productCode, String storeCode, String type, int quantity,
            int stockBefore, int stockAfter, int reservedBefore, int reservedAfter, String reason,
            String referenceType, Long referenceId, String actor, LocalDateTime createdAt) {
        public static InventoryTransactionResponse from(InventoryTransaction t, String productName, String productCode, String storeCode) {
            return new InventoryTransactionResponse(t.getId(), t.getVariantId(), t.getSku(), productName,
                    productCode, storeCode, t.getType().name(), t.getQuantity(),
                    t.getStockBefore(), t.getStockAfter(), t.getReservedBefore(), t.getReservedAfter(),
                    t.getReason(), t.getReferenceType(), t.getReferenceId(), t.getActor(), t.getCreatedAt());
        }
    }
}
