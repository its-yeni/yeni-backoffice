package com.yeni.backoffice.core.commerce.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class PurchaseOrderDtos {
    private PurchaseOrderDtos() {}

    public record PoItemRequest(@NotNull Long variantId, @Positive int orderedQuantity,
            @NotNull @PositiveOrZero BigDecimal unitCost) {}

    public record PoCreateRequest(@NotNull Long supplierId, @NotNull Long storeId,
            LocalDate expectedArrivalDate, String memo,
            @NotEmpty @Valid List<PoItemRequest> items) {}

    public record PoReceiveLine(@NotNull Long itemId, @Positive int quantity,
            LocalDate manufacturedDate, LocalDate expirationDate, String memo) {}

    public record PoReceiveRequest(@NotEmpty @Valid List<PoReceiveLine> lines) {}

    public record PoItemResponse(Long id, Long variantId, String productName, String sku, String optionSummary,
            int orderedQuantity, int receivedQuantity, int outstandingQuantity, BigDecimal unitCost, BigDecimal lineAmount) {}

    public record PoResponse(Long id, String poNo, Long supplierId, String supplierName, Long storeId, String storeName,
            String status, LocalDate expectedArrivalDate, LocalDateTime orderedAt, boolean overdue,
            int orderedTotal, int receivedTotal, BigDecimal totalAmount, String memo, LocalDateTime createdAt,
            List<PoItemResponse> items) {}

    public record PoSummary(long draftCount, long orderedCount, long partialCount, long receivedCount,
            long overdueCount, BigDecimal openOrderAmount) {}
}
