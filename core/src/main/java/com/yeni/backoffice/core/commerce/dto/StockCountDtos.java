package com.yeni.backoffice.core.commerce.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDateTime;
import java.util.List;

public final class StockCountDtos {
    private StockCountDtos() {}

    /** scope: ALL / LOW_STOCK / CATEGORY:<카테고리명> */
    public record StockCountStartRequest(@NotNull Long storeId, String scope, String memo) {}

    public record StockCountLineInput(@NotNull @PositiveOrZero Integer countedQuantity) {}

    public record CountLineResponse(Long id, Long variantId, String productName, String sku, String optionSummary,
            String category, int systemQuantity, Integer countedQuantity, int differenceQuantity, boolean counted) {}

    public record StockCountResponse(Long id, String countNo, Long storeId, String storeName, String status,
            String memo, LocalDateTime completedAt, LocalDateTime createdAt,
            int lineCount, int countedCount, int diffCount, int netDifference, List<CountLineResponse> lines) {}
}
