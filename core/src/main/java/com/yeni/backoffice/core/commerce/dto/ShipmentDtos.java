package com.yeni.backoffice.core.commerce.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class ShipmentDtos {
    private ShipmentDtos() {}

    /** 결제 완료됐지만 아직 출고 처리되지 않은 주문 상품 한 줄. */
    public record ShipmentPendingResponse(Long orderItemId, Long orderId, String orderNo, String buyerName,
            String productName, String sku, String optionSummary, int quantity, BigDecimal itemAmount,
            LocalDateTime paidAt) {}
}
