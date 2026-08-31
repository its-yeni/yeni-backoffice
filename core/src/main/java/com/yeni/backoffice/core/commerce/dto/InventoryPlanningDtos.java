package com.yeni.backoffice.core.commerce.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class InventoryPlanningDtos {
    private InventoryPlanningDtos() {}
    public record LotResponse(Long id, Long storeId, String storeName, Long variantId, String productName,
            String sku, String optionSummary, String lotNo, LocalDate manufacturedDate, LocalDate expirationDate,
            int receivedQuantity, int availableQuantity, Long daysToExpire, String health, String memo) {}
    public record ReplenishmentResponse(Long storeId, String storeName, Long variantId, String productName,
            String sku, String optionSummary, int availableQuantity, int inTransitQuantity, int onOrderQuantity,
            int safetyStock, BigDecimal averageDailySales, int leadTimeDays, String supplierName,
            int reorderPoint, int suggestedOrderQuantity, Integer daysUntilStockout, String health) {}
    public record InsightSummary(long soldOutCount, long reorderCount, long expiringSoonCount,
            long excessCount, BigDecimal atRiskAssetValue) {}
}
