package com.yeni.backoffice.core.commerce.entity;

import com.yeni.backoffice.core.commerce.enums.DeliveryStatus;
import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.ValidationBusinessException;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 배송지 정보와 배송 상태(PREPARING → IN_TRANSIT → DELIVERED → RETURNED)를 관리한다.
 * 주문 1건이 여러 배송(부분배송)을 가질 수 있다 — orderId에 유니크 제약을 두지 않았고, 어떤 주문 상품이
 * 이 배송에 속하는지는 CommerceOrderItem.deliveryId 쪽에서 참조한다(배송 쪽에서 아이템 목록을 들고
 * 있지 않는 이유: 아이템이 이미 자신의 상태(shippedYn)를 갖고 있어, 참조 방향을 하나로 유지하기 위함).
 */
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "commerce_delivery")
public class CommerceDelivery extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long orderId;

    @Column(nullable = false, length = 60) private String receiverName;
    @Column(nullable = false, length = 30) private String receiverPhone;
    @Column(length = 10) private String zipCode;
    @Column(nullable = false, length = 200) private String address1;
    @Column(length = 100) private String address2;
    @Column(length = 200) private String deliveryRequest;

    @Column(length = 40) private String carrier;
    @Column(length = 60) private String trackingNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private DeliveryStatus status;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;
    @Column(length = 200) private String returnReason;
    private LocalDateTime returnedAt;

    /** 배송 시작: 운송장을 발급하고 배송 중 상태로 바꾼다. 이미 시작됐거나 완료된 배송은 다시 시작할 수 없다. */
    public void dispatch(String carrier, String trackingNumber) {
        if (status != DeliveryStatus.PREPARING)
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "배송 준비 상태인 주문만 배송을 시작할 수 있습니다.");
        this.carrier = carrier.trim();
        this.trackingNumber = trackingNumber.trim();
        this.status = DeliveryStatus.IN_TRANSIT;
        this.shippedAt = LocalDateTime.now();
    }

    /** 배송 완료: 배송 중 상태에서만 완료 처리할 수 있다(중복 완료 방지). */
    public void complete() {
        if (status != DeliveryStatus.IN_TRANSIT)
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "배송 중 상태인 주문만 배송 완료 처리할 수 있습니다.");
        this.status = DeliveryStatus.DELIVERED;
        this.deliveredAt = LocalDateTime.now();
    }

    /** 반송: 고객이 물건을 받은(DELIVERED) 뒤에만 반송 처리할 수 있다 — 배송 중 상태에서의 반송(수취 거부 등)은
     * 이 프로젝트 범위 밖으로 남겨둔다(반송 사유·후속 환불/재입고 처리는 별도 반품 도메인이 될 것이다). */
    public void markReturned(String reason) {
        if (status != DeliveryStatus.DELIVERED)
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "배송 완료 상태인 주문만 반송 처리할 수 있습니다.");
        this.status = DeliveryStatus.RETURNED;
        this.returnReason = reason;
        this.returnedAt = LocalDateTime.now();
    }
}
