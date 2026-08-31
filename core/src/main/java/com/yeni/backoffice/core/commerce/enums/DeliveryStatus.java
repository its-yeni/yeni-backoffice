package com.yeni.backoffice.core.commerce.enums;

/**
 * 배송(고객에게 물건이 실제로 도착하기까지) 상태.
 * 재고 관점의 "출고 완료"(ProductVariant.ship, InventoryTransactionType.SHIPMENT)와는 다른 개념이다 —
 * 출고는 창고에서 재고가 빠져나가는 시점(내부 이벤트)이고, 배송은 택배사가 실어 나른 뒤 고객이 받기까지의
 * 과정(고객에게 보이는 이벤트)이다. 실무에서도 이 둘은 보통 서로 다른 시스템(WMS vs 택배사 API)이 담당한다.
 * <p>PREPARING(배송 준비) → IN_TRANSIT(배송 중, 운송장 발급) → DELIVERED(배송 완료) → RETURNED(반송, 배송
 * 완료 이후 고객이 반품을 요청해 물건이 되돌아온 경우) 4단계를 다룬다.
 */
public enum DeliveryStatus {
    PREPARING,
    IN_TRANSIT,
    DELIVERED,
    RETURNED
}
