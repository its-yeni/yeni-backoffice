package com.yeni.backoffice.core.commerce.enums;

/**
 * 반품 귀책. 반송비를 누가 부담하는지가 이 값 하나로 갈린다 — 단순 변심(CUSTOMER_FAULT)이면 반송비를
 * 환불액에서 차감하고, 오배송/불량(SELLER_FAULT)이면 반송비 없이 전액 환불한다.
 */
public enum ReturnResponsibility {
    CUSTOMER_FAULT,
    SELLER_FAULT
}
