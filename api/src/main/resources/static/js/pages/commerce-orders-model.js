window.CommerceOrderModel = (function () {
  const paymentLabels = {
    READY: "결제 대기",
    APPROVED: "결제 승인",
    APPROVE_UNKNOWN: "결과 불명",
    FAILED: "결제 실패"
  };
  const paymentClasses = {READY: "ready", APPROVED: "paid", APPROVE_UNKNOWN: "unknown", FAILED: "failed"};
  const orderLabels = {
    PENDING_PAYMENT: "주문 생성",
    CREATED: "주문 생성",
    PAID: "결제 완료",
    PURCHASE_CONFIRMED: "구매 확정",
    PAYMENT_FAILED: "결제 실패",
    PARTIALLY_CANCELLED: "부분취소",
    CANCELLED: "취소완료"
  };

  function isCancelled(order) {
    return ["CANCELLED", "CANCELED", "PARTIALLY_CANCELLED"].includes(order.orderStatus);
  }

  const quickFilters = [
    {key: "", label: "전체", match: () => true},
    {key: "READY", label: "결제 대기", match: order => order.paymentStatus === "READY"},
    {key: "APPROVED", label: "결제 승인", match: order => order.paymentStatus === "APPROVED" && !isCancelled(order)},
    {key: "FAILED", label: "결제 실패", match: order => order.paymentStatus === "FAILED"},
    {key: "CANCELLED", label: "취소", match: isCancelled}
  ];
  const extraFilters = [
    {key: "APPROVE_UNKNOWN", match: order => order.paymentStatus === "APPROVE_UNKNOWN"}
  ];
  const scenarios = [
    {value: "NORMAL", label: "정상 승인", recommend: true},
    {value: "RESULT_UNKNOWN", label: "결과 불명 (응답 딜레이)"},
    {value: "INTERNAL_FAIL", label: "승인 후 내부 처리 실패 (망취소)"},
    {value: "PAYMENT_METHOD_ERROR", label: "결제 수단 오류"},
    {value: "CARD_LIMIT_EXCEEDED", label: "카드 한도 부족"},
    {value: "DUPLICATE_REQUEST", label: "듀플리케이션 테스트 (중복 요청)"}
  ];

  function fulfillmentStatus(order) {
    if (["PENDING_PAYMENT", "CREATED"].includes(order.orderStatus)) return {label: "주문 생성", tone: "ready"};
    if (order.orderStatus === "PAYMENT_FAILED") return {label: "결제 실패", tone: "failed"};
    if (isCancelled(order)) {
      return {label: order.orderStatus === "PARTIALLY_CANCELLED" ? "부분취소" : "취소완료", tone: "ready"};
    }
    if (order.orderStatus === "PURCHASE_CONFIRMED") return {label: "구매 확정", tone: "paid"};
    if (order.orderStatus !== "PAID") return {label: orderLabels[order.orderStatus] || order.orderStatus, tone: "ready"};

    const shippable = (order.items || []).filter(item => item.productVariantId);
    const delivery = order.delivery;
    if (delivery?.status === "DELIVERED") return {label: "배송 완료", tone: "paid"};
    if (delivery?.status === "IN_TRANSIT") return {label: "배송 중", tone: "unknown"};
    if (delivery?.status === "RETURNED") return {label: "반송", tone: "failed"};
    if (!shippable.length) return {label: "결제 완료", tone: "paid"};
    if (shippable.every(item => item.shippedYn)) return {label: delivery ? "배송 준비" : "출고 완료", tone: "unknown"};
    return {label: "출고 대기", tone: "unknown"};
  }

  return {
    paymentLabels,
    paymentClasses,
    quickFilters,
    extraFilters,
    scenarios,
    isCancelled,
    fulfillmentStatus
  };
})();
