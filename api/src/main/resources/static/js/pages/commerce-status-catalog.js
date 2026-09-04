window.CommerceStatusCatalog = Object.freeze({
  delivery: Object.freeze({
    labels: Object.freeze({PREPARING: "배송 준비", IN_TRANSIT: "배송 중", DELIVERED: "배송 완료", RETURNED: "반송"}),
    tones: Object.freeze({PREPARING: "", IN_TRANSIT: "is-warning", DELIVERED: "is-success", RETURNED: "is-danger"})
  }),
  returns: Object.freeze({
    labels: Object.freeze({REQUESTED: "접수", INSPECTING: "검수 중", COMPLETED: "완료", REJECTED: "반려"}),
    tones: Object.freeze({REQUESTED: "", INSPECTING: "is-warning", COMPLETED: "is-success", REJECTED: "is-danger"}),
    responsibilityLabels: Object.freeze({CUSTOMER_FAULT: "고객 귀책", SELLER_FAULT: "판매자 귀책"}),
    refundLabels: Object.freeze({NOT_REQUIRED: "환불 불필요", PENDING: "환불 대기", SUCCESS: "환불 완료", FAILED: "환불 실패"}),
    refundTones: Object.freeze({PENDING: "is-warning", SUCCESS: "is-success", FAILED: "is-danger"})
  }),
  purchaseOrder: Object.freeze({
    labels: Object.freeze({DRAFT: "작성 중", ORDERED: "발주 완료", PARTIALLY_RECEIVED: "부분 입고", RECEIVED: "입고 완료", CANCELED: "취소"})
  }),
  stockCount: Object.freeze({
    labels: Object.freeze({IN_PROGRESS: "진행 중", COMPLETED: "반영 완료", CANCELED: "취소"})
  }),
  inventoryTransfer: Object.freeze({
    labels: Object.freeze({REQUESTED: "이동 요청", IN_TRANSIT: "이동 중", RECEIVED: "입고 완료", CANCELLED: "취소"})
  }),
  inventoryTransaction: Object.freeze({
    typeLabels: Object.freeze({RECEIPT: "입고", RESERVE: "주문 예약", RELEASE: "예약 해제", SHIPMENT: "출고 완료", ADJUST_IN: "증가 조정", ADJUST_OUT: "감소 조정", TRANSFER_IN: "이동 입고", TRANSFER_OUT: "이동 출고"}),
    referenceLabels: Object.freeze({ORDER: "주문", MANUAL: "수동 작업", SHIPMENT: "출고", PURCHASE_ORDER: "발주", STOCK_COUNT: "재고 실사", RETURN: "반품", TRANSFER: "재고 이동"})
  })
});
