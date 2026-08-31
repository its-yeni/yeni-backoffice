package com.yeni.backoffice.core.commerce.dto;

import com.yeni.backoffice.core.commerce.entity.CommerceDelivery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDateTime;
import java.util.List;

public final class CommerceDeliveryDtos {
    private CommerceDeliveryDtos() {}

    /** 주문 생성 시 함께 받는 배송지 정보. address1이 비어 있으면 배송 레코드를 만들지 않는다(주소 없이도
     * 기존 Mock 주문 생성 흐름은 그대로 동작해야 하므로 필수로 강제하지 않는다). */
    public record DeliveryAddressRequest(String receiverName, String receiverPhone, String zipCode,
            String address1, String address2, String deliveryRequest) {}

    public record DispatchRequest(@NotBlank(message = "택배사를 입력해 주세요.") String carrier,
            @NotBlank(message = "운송장 번호를 입력해 주세요.") String trackingNumber) {}

    /** 부분배송 분리: 지정한 주문 상품들을 원래 배송에서 떼어내 같은 주소로 나가는 새 배송 건을 만든다. */
    public record SplitDeliveryRequest(@NotEmpty(message = "분리할 주문 상품을 1개 이상 선택해 주세요.") List<Long> orderItemIds) {}

    /** 송장 일괄등록 한 줄. 실무에서 엑셀/CSV로 관리하는 "주문번호-택배사-운송장번호" 매핑을 그대로 받는다. */
    public record BulkDispatchRow(@NotBlank(message = "주문번호가 비어 있습니다.") String orderNo,
            @NotBlank(message = "택배사가 비어 있습니다.") String carrier,
            @NotBlank(message = "운송장 번호가 비어 있습니다.") String trackingNumber) {}

    public record BulkDispatchRequest(@NotEmpty(message = "등록할 행이 없습니다.") @Valid List<BulkDispatchRow> rows) {}

    public record BulkDispatchResult(String orderNo, boolean success, String message) {}

    public record DeliveryResponse(Long id, Long orderId, String orderNo, String buyerName,
            String receiverName, String receiverPhone, String zipCode, String address1, String address2,
            String deliveryRequest, String carrier, String trackingNumber, String status,
            LocalDateTime shippedAt, LocalDateTime deliveredAt, String returnReason, LocalDateTime returnedAt,
            LocalDateTime createdAt, int unshippedItemCount) {
        public static DeliveryResponse from(CommerceDelivery d, String orderNo, String buyerName, int unshippedItemCount) {
            return new DeliveryResponse(d.getId(), d.getOrderId(), orderNo, buyerName,
                    d.getReceiverName(), d.getReceiverPhone(), d.getZipCode(), d.getAddress1(), d.getAddress2(),
                    d.getDeliveryRequest(), d.getCarrier(), d.getTrackingNumber(), d.getStatus().name(),
                    d.getShippedAt(), d.getDeliveredAt(), d.getReturnReason(), d.getReturnedAt(), d.getCreatedAt(),
                    unshippedItemCount);
        }
    }
}
