package com.yeni.backoffice.core.commerce.dto;

import com.yeni.backoffice.core.commerce.entity.CommerceOrder;
import com.yeni.backoffice.core.commerce.entity.CommerceOrderItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class CommerceOrderDtos {
    private CommerceOrderDtos() {}

    public record CommerceOrderCreateRequest(
            String orderNo,
            @NotBlank(message = "구매자명은 필수입니다.") String buyerName,
            String buyerPhone,
            @Valid @NotEmpty(message = "주문 상품은 1개 이상이어야 합니다.") List<CommerceOrderItemCreateRequest> items,
            CommerceDeliveryDtos.DeliveryAddressRequest delivery) {
        public CommerceOrderCreateRequest(String orderNo, String buyerName, String buyerPhone, List<CommerceOrderItemCreateRequest> items) {
            this(orderNo, buyerName, buyerPhone, items, null);
        }
    }

    public record CommerceOrderItemCreateRequest(
            @NotNull(message = "상품 ID는 필수입니다.") Long productId,
            @Positive(message = "상품 수량은 1 이상이어야 합니다.") int quantity,
            List<Long> optionValueIds,
            List<CommerceOrderAddonCreateRequest> addOns) {
        public CommerceOrderItemCreateRequest(Long productId,int quantity){this(productId,quantity,List.of(),List.of());}
    }
    public record CommerceOrderAddonCreateRequest(@NotNull Long addonGroupId,@NotNull Long productId,@Positive int quantity) {}

    public record MockScenarioOrderRequest(
            @NotBlank(message = "시나리오는 필수입니다.") String scenario,
            String buyerName,
            String buyerPhone,
            List<CommerceOrderItemCreateRequest> items,
            CommerceDeliveryDtos.DeliveryAddressRequest delivery) {}

    public record MockScenarioOrderResponse(CommerceOrderResponse order, String scenario, String scenarioLabel, String resultMessage) {}

    public record CommerceOrderResponse(
            Long id, String orderNo, String buyerName, String buyerPhone, String productName, int itemCount,
            BigDecimal productAmount, BigDecimal deliveryFee, BigDecimal discountAmount, BigDecimal payableAmount,
            BigDecimal paidAmount, BigDecimal cancelledAmount, String orderStatus, String paymentStatus,
            Long paymentId, String tid, String lastMessage, List<CommerceOrderItemResponse> items,
            CommerceDeliveryDtos.DeliveryResponse delivery, Long storeId, String channelType,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        public static CommerceOrderResponse from(CommerceOrder order, List<CommerceOrderItem> items) {
            return from(order, items, null);
        }
        public static CommerceOrderResponse from(CommerceOrder order, List<CommerceOrderItem> items, CommerceDeliveryDtos.DeliveryResponse delivery) {
            return new CommerceOrderResponse(
                    order.getId(), order.getOrderNo(), order.getBuyerName(), order.getBuyerPhone(), order.getProductName(),
                    items.size(), order.getProductAmount(), order.getDeliveryFee(), order.getDiscountAmount(),
                    order.getPayableAmount(), order.getPaidAmount(), order.getCancelledAmount(),
                    order.getOrderStatus().name(), order.getPaymentStatus().name(), order.getPaymentId(), order.getTid(),
                    order.getLastMessage(), items.stream().map(CommerceOrderItemResponse::from).toList(), delivery,
                    order.getStoreId(), order.getChannelType() == null ? "WEB" : order.getChannelType(),
                    order.getCreatedAt(), order.getUpdatedAt());
        }
    }

    public record CommerceOrderItemResponse(
            Long id, Long productId, String productCode, String productName, String categoryName,
            BigDecimal unitPrice, int quantity, BigDecimal itemAmount,String optionSummary,BigDecimal optionAdditionalAmount,boolean addonItem,
            Long productVariantId, boolean shippedYn, Long deliveryId) {
        public static CommerceOrderItemResponse from(CommerceOrderItem item) {
            return new CommerceOrderItemResponse(item.getId(), item.getProductId(), item.getProductCode(),
                    item.getProductName(), item.getCategoryName() == null ? "미분류" : item.getCategoryName(),
                    item.getUnitPrice(), item.getQuantity(), item.getItemAmount(),item.getOptionSummary(),item.getOptionAdditionalAmount(),item.isAddonItem(),
                    item.getProductVariantId(), item.isShippedYn(), item.getDeliveryId());
        }
    }

    public record CommerceOrderSummaryResponse(
            long totalCount, long paidCount, long paymentReadyCount, long paymentUnknownCount, BigDecimal totalOrderAmount) {
        public static CommerceOrderSummaryResponse from(List<CommerceOrder> orders) {
            return new CommerceOrderSummaryResponse(
                    orders.size(),
                    orders.stream().filter(order -> "PAID".equals(order.getOrderStatus().name())).count(),
                    orders.stream().filter(order -> "READY".equals(order.getPaymentStatus().name())).count(),
                    orders.stream().filter(order -> "APPROVE_UNKNOWN".equals(order.getPaymentStatus().name())).count(),
                    orders.stream().map(CommerceOrder::getPayableAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        }
    }
}
