(function () {
  const $ = id => document.getElementById(id);

  const PAYMENT_LABEL = { READY: "결제 대기", APPROVED: "결제 승인", APPROVE_UNKNOWN: "결과 불명", FAILED: "결제 실패" };
  const PAYMENT_CLASS = { READY: "ready", APPROVED: "paid", APPROVE_UNKNOWN: "unknown", FAILED: "failed" };
  const ORDER_LABEL = {
    PENDING_PAYMENT: "주문 생성", CREATED: "주문 생성", PAID: "결제 완료", PURCHASE_CONFIRMED: "구매 확정",
    PAYMENT_FAILED: "결제 실패", PARTIALLY_CANCELLED: "부분취소", CANCELLED: "취소완료"
  };
  // 주문 목록 위 요약 칩(전체/결제대기/결제승인/결제실패/취소) — 결제상태만으로는 "취소"를 표현할 수 없어서
  // (취소는 결제 승인 이후 주문 상태가 바뀌는 것이라 paymentStatus는 그대로 APPROVED로 남는다)
  // 결제상태 기반 3개 + 주문상태 기반 취소 1개를 섞은, 화면에 보여줄 것 기준의 상호배타적 그룹이다.
  // 칩으로 노출할 상태(QUICK_FILTERS)와, 상단 "결제상태" 드롭다운에서만 고를 수 있는 상태(EXTRA_FILTERS) —
  // 드롭다운은 원래 paymentStatus 값 그대로를 옵션으로 갖고 있어서 칩에 없는 값도 선택될 수 있다.
  const QUICK_FILTERS = [
    { key: "", label: "전체", match: () => true },
    { key: "READY", label: "결제 대기", match: o => o.paymentStatus === "READY" },
    { key: "APPROVED", label: "결제 승인", match: o => o.paymentStatus === "APPROVED" && !isCancelledOrder(o) },
    { key: "FAILED", label: "결제 실패", match: o => o.paymentStatus === "FAILED" },
    { key: "CANCELLED", label: "취소", match: o => isCancelledOrder(o) }
  ];
  const EXTRA_FILTERS = [
    { key: "APPROVE_UNKNOWN", match: o => o.paymentStatus === "APPROVE_UNKNOWN" }
  ];
  function isCancelledOrder(order) { return order.orderStatus === "CANCELLED" || order.orderStatus === "CANCELED" || order.orderStatus === "PARTIALLY_CANCELLED"; }
  // 배송 단계는 실제 CommerceDelivery.status(PREPARING→IN_TRANSIT→DELIVERED→RETURNED)를 반영한다.
  // 출고(재고가 창고에서 빠지는 shippedYn)와 배송(택배사 이동)은 별개 단계다.
  function fulfillmentStatus(order) {
    if (order.orderStatus === "PENDING_PAYMENT" || order.orderStatus === "CREATED") return { label: "주문 생성", tone: "ready" };
    if (order.orderStatus === "PAYMENT_FAILED") return { label: "결제 실패", tone: "failed" };
    if (isCancelledOrder(order)) return { label: order.orderStatus === "PARTIALLY_CANCELLED" ? "부분취소" : "취소완료", tone: "ready" };
    if (order.orderStatus === "PURCHASE_CONFIRMED") return { label: "구매 확정", tone: "paid" };
    if (order.orderStatus === "PAID") {
      const shippable = (order.items || []).filter(item => item.productVariantId);
      const delivery = order.delivery;
      if (delivery) {
        if (delivery.status === "DELIVERED") return { label: "배송 완료", tone: "paid" };
        if (delivery.status === "IN_TRANSIT") return { label: "배송 중", tone: "unknown" };
        if (delivery.status === "RETURNED") return { label: "반송", tone: "failed" };
      }
      if (!shippable.length) return { label: "결제 완료", tone: "paid" };
      if (shippable.every(item => item.shippedYn)) return { label: delivery ? "배송 준비" : "출고 완료", tone: "unknown" };
      return { label: "출고 대기", tone: "unknown" };
    }
    return { label: ORDER_LABEL[order.orderStatus] || order.orderStatus, tone: "ready" };
  }
  const SCENARIOS = [
    { value: "NORMAL", label: "정상 승인", recommend: true },
    { value: "RESULT_UNKNOWN", label: "결과 불명 (응답 딜레이)" },
    { value: "INTERNAL_FAIL", label: "승인 후 내부 처리 실패 (망취소)" },
    { value: "PAYMENT_METHOD_ERROR", label: "결제 수단 오류" },
    { value: "CARD_LIMIT_EXCEEDED", label: "카드 한도 부족" },
    { value: "DUPLICATE_REQUEST", label: "듀플리케이션 테스트 (중복 요청)" }
  ];

  let pagination, sorter, filteredOrders = [], products = [], allOrders = [], activeStatus = "", selectedScenario = "NORMAL";

  document.addEventListener("DOMContentLoaded", async () => {
    const requestedKeyword = new URLSearchParams(location.search).get('keyword');
    if (requestedKeyword && $("orderKeyword")) $("orderKeyword").value = requestedKeyword;
    setupTable();
    bindTabs();
    bindListControls();
    renderScenarios();
    bindMockForm();
    document.querySelectorAll("[data-close-detail]").forEach(el => el.onclick = event => { event.preventDefault(); closeDetail(); });
    document.addEventListener("keydown", event => { if (event.key === "Escape") closeDetail(); });
    await loadProducts();
    if ($("storeInput")) $("storeInput").value = activeStoreCode() || "현재 선택 매장";
    await addRow();
    await refresh();
    const requestedOrderId = Number(new URLSearchParams(location.search).get('orderId'));
    if (requestedOrderId) await openDetail(requestedOrderId);
  });

  function bindTabs() {
    document.querySelectorAll(".page-tabs button").forEach(btn => btn.onclick = () => {
      document.querySelectorAll(".page-tabs button").forEach(b => b.classList.remove("active"));
      document.querySelectorAll(".page-tab-panel").forEach(p => p.classList.remove("active"));
      btn.classList.add("active");
      $("tab-" + btn.dataset.tab).classList.add("active");
    });
  }

  function setupTable() {
    pagination = AdminPagination.mount($("orderPagination"), { total: 0, size: 20, onChange: renderRows });
    sorter = AdminTableSort.attach(document.querySelector(".order-list-table"), {
      defaultKey: "createdAt", defaultDir: "desc", onSort: renderRows
    });
  }

  function renderRows() {
    const sorted = sorter.apply(filteredOrders);
    pagination.setTotal(sorted.length);
    $("orderEmpty").hidden = sorted.length > 0;
    const rowStart = (pagination.getPage() - 1) * pagination.getSize();
    $("orderRows").innerHTML = pagination.slice(sorted).map((order, i) => {
      const fulfil = fulfillmentStatus(order);
      const unshipped = unshippedItems(order);
      const actions = [`<button type="button" class="order-row-action" data-a="detail">상세</button>`];
      if (order.orderStatus === "PENDING_PAYMENT") actions.push(`<button type="button" class="order-row-action primary" data-a="pay">결제 처리</button>`);
      if (order.orderStatus === "PAID" && unshipped.length) actions.push(`<button type="button" class="order-row-action primary" data-a="ship">출고 완료</button>`);
      return `<tr data-order="${order.id}">
        <td class="row-index">${rowStart + i + 1}</td>
        <td><strong>${escapeHtml(order.orderNo || "-")}</strong></td>
        <td><small>${escapeHtml((order.createdAt || "").replace("T", " ").slice(0, 16))}</small></td>
        <td>${escapeHtml(order.buyerName || "-")}</td>
        <td><span class="order-product-name">${escapeHtml(order.productName || "-")}</span></td>
        <td class="amount">${money(order.payableAmount)}</td>
        <td>${badge(order.paymentStatus)}</td>
        <td>${badge2(fulfil)}</td>
        <td class="actions">${actions.join("")}</td>
      </tr>`;
    }).join("");
    $("orderRows").querySelectorAll("tr[data-order]").forEach(tr => {
      const id = Number(tr.dataset.order);
      tr.onclick = event => {
        const btn = event.target.closest("[data-a]");
        if (event.target.closest(".copy-chip")) return;
        if (!btn) { openDetail(id); return; }
        onRowAction(btn.dataset.a, allOrders.find(o => o.id === id));
      };
    });
  }

  async function onRowAction(action, order) {
    if (!order) return;
    if (action === "detail") { openDetail(order.id); return; }
    if (action === "pay") {
      try { await apiPost(`/admin/api/commerce/orders/${order.id}/pay`, {}); await refresh(); AppToast.success(`${order.orderNo} 주문의 결제를 승인 처리했습니다.`); }
      catch (error) { AppToast.error(error.message); }
    }
    if (action === "ship") {
      try {
        for (const item of unshippedItems(order)) await apiPost(`/admin/api/commerce/shipments/${item.id}/complete`, {});
        await refresh();
        AppToast.success(`${order.orderNo} 주문을 출고 완료 처리했습니다.`);
      } catch (error) { AppToast.error(error.message); }
    }
  }

  function badge(status) {
    return `<span class="order-status-text ${PAYMENT_CLASS[status] || "ready"}"><i></i>${escapeHtml(PAYMENT_LABEL[status] || status)}</span>`;
  }
  function badge2({ label, tone }) {
    return `<span class="order-status-text ${tone}"><i></i>${escapeHtml(label)}</span>`;
  }

  function unshippedItems(order) { return (order.items || []).filter(item => item.productVariantId && !item.shippedYn); }

  function bindListControls() {
    $("orderSearchBtn").onclick = applyFilters;
    $("orderRefreshBtn").onclick = () => {
      $("filterDateFrom").value = ""; $("filterDateTo").value = ""; $("filterPaymentStatus").value = "";
      $("orderKeyword").value = ""; activeStatus = ""; applyFilters();
    };
    $("filterPaymentStatus").onchange = () => { activeStatus = $("filterPaymentStatus").value; applyFilters(); };
    $("filterDateFrom").onchange = applyFilters;
    $("filterDateTo").onchange = applyFilters;
    $("orderKeyword").addEventListener("keydown", event => { if (event.key === "Enter") applyFilters(); });
  }

  function renderQuickFilters() {
    $("quickFilters").innerHTML = QUICK_FILTERS.map(chip => `
      <button type="button" class="quick-filter ${chip.key === activeStatus ? "active" : ""}" data-status="${chip.key}">
        <span class="dot"></span>${escapeHtml(chip.label)} ${allOrders.filter(chip.match).length}
      </button>`).join("");
    document.querySelectorAll(".quick-filter").forEach(btn => btn.onclick = () => {
      activeStatus = btn.dataset.status;
      $("filterPaymentStatus").value = activeStatus;
      applyFilters();
    });
  }

  function applyFilters() {
    const keyword = $("orderKeyword").value.trim().toLowerCase();
    const from = $("filterDateFrom").value;
    const to = $("filterDateTo").value;
    const activeFilter = QUICK_FILTERS.find(f => f.key === activeStatus) || EXTRA_FILTERS.find(f => f.key === activeStatus) || QUICK_FILTERS[0];
    filteredOrders = allOrders.filter(order => {
      if (!activeFilter.match(order)) return false;
      if (keyword && !((order.orderNo || "").toLowerCase().includes(keyword) || (order.buyerName || "").toLowerCase().includes(keyword))) return false;
      const day = (order.createdAt || "").slice(0, 10);
      if (from && day < from) return false;
      if (to && day > to) return false;
      return true;
    });
    pagination.setPage(1);
    renderRows();
    renderQuickFilters();
  }

  async function refresh() {
    allOrders = await apiGet("/admin/api/commerce/orders");
    applyFilters();
  }

  /* ---- 주문 상세 ---- */
  async function openDetail(orderId) {
    const modal = $("order-detail-modal");
    modal.classList.add("open"); modal.setAttribute("aria-hidden", "false"); document.body.style.overflow = "hidden";
    $("order-detail-body").innerHTML = `<p class="preview-loading">불러오는 중...</p>`;
    try {
      const order = await apiGet(`/admin/api/commerce/orders/${orderId}`);
      let trace = null;
      if (order.paymentId) {
        try { trace = await apiGet(`/admin/api/payment-operations/payments/${order.paymentId}/trace`); }
        catch (error) { console.warn("Payment trace could not be loaded", error); }
      }
      let deliveries = [];
      try { deliveries = await apiGet(`/admin/api/commerce/orders/${orderId}/deliveries`); } catch (error) { /* 배송지 없는 주문일 수 있음 */ }
      let orderReturns = [];
      try { orderReturns = await apiGet(`/admin/api/commerce/returns/order/${orderId}`); } catch (error) { /* 반품 이력 없음 */ }
      renderDetail(order, trace, deliveries, orderReturns);
    } catch (error) {
      $("order-detail-body").innerHTML = `<p class="preview-message">${escapeHtml(error.message)}</p>`;
    }
  }
  function closeDetail() {
    const modal = $("order-detail-modal");
    modal.classList.remove("open"); modal.setAttribute("aria-hidden", "true"); document.body.style.overflow = "";
  }
  function traceTime(value) { return value ? escapeHtml(value.replace("T", " ").slice(0, 19)) : "-"; }
  function traceTone(status) {
    const value=String(status||"").toUpperCase();
    if(value.includes("FAIL"))return "failed";
    if(value.includes("UNKNOWN")||value.includes("RETRY")||value.includes("READY"))return "attention";
    return "complete";
  }
  function buildTraceTimeline(order, trace) {
    const steps=[{time:order.createdAt,title:"주문 생성",meta:`${(order.items||[]).length}개 품목 · 서버 가격 검증`,tone:"complete"}];
    (order.items||[]).filter(item=>item.productVariantId).forEach(item=>steps.push({time:order.createdAt,title:"SKU 재고 예약",meta:`${item.productCode} · ${item.quantity}개`,tone:"complete"}));
    if(!trace){
      if(order.paymentId)steps.push({time:order.updatedAt,title:"결제 추적 데이터 확인 필요",meta:order.lastMessage||"Trace API 응답을 불러오지 못했습니다.",tone:"attention"});
      else steps.push({time:order.updatedAt,title:"결제 대기",meta:order.lastMessage||"PG 승인 전입니다.",tone:"attention"});
      return steps;
    }
    (trace.pgLogs||[]).slice().reverse().forEach(log=>steps.push({time:log.loggedAt,title:`PG ${log.apiType||"요청"}`,meta:`requestId ${log.requestId||"-"} · ${log.resultMessage||log.resultStatus||"처리"}`,tone:traceTone(log.resultStatus)}));
    if(trace.payment)steps.push({time:trace.payment.approvedAt||order.updatedAt,title:`결제 ${trace.payment.paymentStatus==="APPROVED"?"승인":"상태 확정"}`,meta:`paymentId ${trace.payment.id} · TID ${trace.payment.tid||"-"}`,tone:traceTone(trace.payment.paymentStatus)});
    (trace.sales||[]).forEach(sale=>steps.push({time:sale.occurredAt,title:`${sale.saleType} 매출 원장 생성`,meta:`원장 #${sale.id} · ${money(sale.totalAmount)}`,tone:traceTone(sale.saleStatus)}));
    (trace.externalSends||[]).forEach(send=>steps.push({time:send.lastSentAt,title:`${send.targetSystem||"외부 시스템"} 전송`,meta:`${send.sendStatus} · 재시도 ${send.retryCount||0}/${send.maxRetryCount||0}`,tone:traceTone(send.sendStatus)}));
    (trace.alimtalkQueues||[]).forEach(queue=>steps.push({time:queue.sentAt,title:"알림 후속처리",meta:`${queue.status} · 재시도 ${queue.retryCount||0}/${queue.maxRetryCount||0}`,tone:traceTone(queue.status)}));
    (trace.recoveryTasks||[]).forEach(task=>steps.push({time:task.processedAt||task.lastTriedAt||task.createdAt,title:`복구 작업 · ${task.recoveryType}`,meta:`${task.status} · 재시도 ${task.retryCount||0}/${task.maxRetryCount||0} · ${task.taskKey}`,tone:traceTone(task.status)}));
    (trace.settlementDetails||[]).forEach(detail=>steps.push({time:null,title:"정산 명세 반영",meta:`명세 #${detail.settlementStatementId} · 지급 대상 ${money(detail.netAmount)}`,tone:"complete"}));
    return steps;
  }
  const DELIVERY_STATUS_LABEL = { PREPARING: "배송 준비", IN_TRANSIT: "배송 중", DELIVERED: "배송 완료", RETURNED: "반송" };
  const DELIVERY_STATUS_TONE = { IN_TRANSIT: "is-warning", DELIVERED: "is-success", RETURNED: "is-danger" };
  const RETURN_STATUS_LABEL = { REQUESTED: "접수", INSPECTING: "검수 중", COMPLETED: "완료", REJECTED: "반려" };
  const RETURN_STATUS_TONE = { INSPECTING: "is-warning", COMPLETED: "is-success", REJECTED: "is-danger" };
  const REFUND_STATUS_LABEL = { NOT_REQUIRED: "환불 불필요", PENDING: "환불 대기", SUCCESS: "환불 완료", FAILED: "환불 실패" };
  const REFUND_STATUS_TONE = { PENDING: "is-warning", SUCCESS: "is-success", FAILED: "is-danger" };

  // 이미 반품이 진행 중/완료된 배송(반려는 제외)은 다시 "반품 접수" 버튼을 노출하지 않는다 —
  // 서버(CommerceReturnService)도 같은 규칙으로 중복 접수를 막고 있어서, 버튼 자체를 안 보여주는 게
  // 클릭했다가 에러 메시지로 튕기는 것보다 낫다.
  function deliveryHasActiveReturn(deliveryId, orderReturns) {
    return (orderReturns || []).some(r => r.deliveryId === deliveryId && r.status !== "REJECTED");
  }

  // 아직 출고되지 않은 SKU 상품만 부분배송으로 분리할 수 있다(이미 나간 상품은 다른 상자로 옮길 수 없으므로).
  function splittableItems(order) { return (order.items || []).filter(item => item.productVariantId && !item.shippedYn); }

  function renderDetail(order, trace, deliveries, orderReturns) {
    const timeline=buildTraceTimeline(order,trace);
    const splittable = splittableItems(order);
    const shippable=(order.items||[]).filter(item=>item.productVariantId);
    const shipped=shippable.filter(item=>item.shippedYn).length;
    const hasLedger=!!(trace&&trace.sales&&trace.sales.length);
    const hasSettlement=!!(trace&&trace.settlementDetails&&trace.settlementDetails.length);
    const phaseState={order:true,payment:order.paymentStatus==='APPROVED',shipment:shippable.length>0&&shipped===shippable.length,ledger:hasLedger,settlement:hasSettlement};
    $("order-detail-body").innerHTML = `
      <section class="order-operation-overview">
        <div class="operation-progress" aria-label="주문 운영 처리 단계">
          <div class="done"><b>1</b><span>주문·가격 검증</span><small>서버 재계산</small></div>
          <div class="${phaseState.payment?'done':order.paymentStatus==='FAILED'?'blocked':'current'}"><b>2</b><span>결제</span><small>${escapeHtml(PAYMENT_LABEL[order.paymentStatus]||order.paymentStatus)}</small></div>
          <div class="${phaseState.shipment?'done':phaseState.payment?'current':''}"><b>3</b><span>출고·원가</span><small>${shippable.length?`${shipped}/${shippable.length}건`:'대상 없음'}</small></div>
          <div class="${phaseState.ledger?'done':''}"><b>4</b><span>매출 원장</span><small>${phaseState.ledger?'반영 완료':'대기'}</small></div>
          <div class="${phaseState.settlement?'done':''}"><b>5</b><span>정산·대사</span><small>${phaseState.settlement?'명세 연결':'대기'}</small></div>
        </div>
        <details class="operation-design-notes" open>
          <summary><span><strong>이 주문에 적용된 운영 설계</strong><small>무엇을 고려해 구성했는지 확인</small></span><span>접기</span></summary>
          <dl>
            <div><dt>재고 정합성</dt><dd>주문 시 가용재고를 예약하고, 실제 수량과 매출원가는 출고 시 확정합니다. 실패·전액취소 시 예약만 복원합니다.</dd></div>
            <div><dt>결제 신뢰성</dt><dd>클라이언트 금액을 신뢰하지 않고 서버 가격을 재계산합니다. 승인 요청은 멱등키로 중복 거래를 차단합니다.</dd></div>
            <div><dt>실패 복구</dt><dd>결과 불명과 승인 후 내부 실패를 정상 실패와 분리하고, 재조회 또는 망취소 복구 작업으로 남깁니다.</dd></div>
            <div><dt>정산 근거</dt><dd>승인·취소를 SALE/CANCEL 원장으로 보존하고, 외부 PG 정산 파일과 대사한 결과를 확정 근거로 사용합니다.</dd></div>
          </dl>
          <p><b>포트폴리오 범위</b> 결제·매출·정산 경험을 기반으로 상품·위치 재고·출고 원가·외부 대사까지 학습 확장한 구현입니다.</p>
        </details>
      </section>
      <section class="detail-section">
        <h4>주문 정보</h4>
        <div class="info-grid">
          <div><span>주문번호</span><strong>${escapeHtml(order.orderNo)}</strong></div>
          <div><span>주문일시</span><strong>${escapeHtml((order.createdAt || "").replace("T", " ").slice(0, 16))}</strong></div>
          <div><span>구매자</span><strong>${escapeHtml(order.buyerName)}</strong></div>
          <div><span>연락처</span><strong>${escapeHtml(order.buyerPhone || "-")}</strong></div>
          <div><span>결제상태</span><strong>${badge(order.paymentStatus)}</strong></div>
          <div><span>주문상태</span><strong>${badge2(fulfillmentStatus(order))}</strong></div>
          <div><span>결제ID</span><strong>${escapeHtml(order.tid || "-")}</strong></div>
          <div><span>주문금액</span><strong>${money(order.payableAmount)}</strong></div>
        </div>
      </section>
      <section class="detail-section">
        <h4>주문 상품</h4>
        <table class="detail-item-table"><thead><tr>${splittable.length > 1 ? "<th></th>" : ""}<th>분류</th><th>상품</th><th>옵션</th><th>단가</th><th>수량</th><th>금액</th><th>배송</th></tr></thead>
        <tbody>${(order.items || []).map(item => `
          <tr>${splittable.length > 1 ? `<td>${item.productVariantId && !item.shippedYn ? `<input type="checkbox" data-split-item="${item.id}">` : ""}</td>` : ""}<td><small>${escapeHtml(item.categoryName || "미분류")}</small></td><td>${item.addonItem ? "추가 · " : ""}${escapeHtml(item.productName)}</td><td>${escapeHtml(item.optionSummary || "-")}</td>
          <td>${money(item.unitPrice)}</td><td>${item.quantity}</td><td>${money(item.itemAmount)}</td>
          <td>${item.deliveryId ? `<a class="text-link" href="/admin/commerce/deliveries?deliveryId=${item.deliveryId}"><small>배송 #${item.deliveryId}${item.shippedYn ? " · 출고완료" : ""}</small></a>` : item.shippedYn ? "<small>출고완료</small>" : "<small>-</small>"}</td></tr>`).join("")}</tbody></table>
        ${splittable.length > 1 ? `<div class="split-delivery-row"><button type="button" class="btn btn-light" id="split-delivery-btn">선택 상품 부분배송 분리</button><span class="split-delivery-hint">아직 출고되지 않은 상품만 다른 배송으로 나눠 보낼 수 있습니다.</span></div>` : ""}
      </section>
      <section class="detail-section">
        <h4>배송 정보</h4>
        ${(deliveries || []).length ? `<table class="detail-item-table"><thead><tr><th>배송</th><th>받는 분</th><th>배송지</th><th>택배사/운송장</th><th>상태</th><th></th></tr></thead>
        <tbody>${deliveries.map(d => `<tr><td><a class="text-link" href="/admin/commerce/deliveries?deliveryId=${d.id}">#${d.id}</a></td><td>${escapeHtml(d.receiverName)}<br><small>${escapeHtml(d.receiverPhone)}</small></td><td><small>${escapeHtml(d.address1)}${d.address2 ? " " + escapeHtml(d.address2) : ""}</small></td><td>${d.carrier ? `${escapeHtml(d.carrier)}<br><small>${escapeHtml(d.trackingNumber)}</small>` : "<small>미발급</small>"}</td><td><span class="status-indicator ${DELIVERY_STATUS_TONE[d.status] || ""}">${DELIVERY_STATUS_LABEL[d.status] || d.status}</span></td>
          <td>${d.status === "DELIVERED" && !deliveryHasActiveReturn(d.id, orderReturns) ? `<a class="text-link" href="/admin/commerce/returns?orderId=${order.id}&deliveryId=${d.id}">반품 접수 →</a>` : ""}</td></tr>`).join("")}</tbody></table>
        <a class="text-link" href="/admin/commerce/deliveries?orderNo=${encodeURIComponent(order.orderNo)}">이 주문 배송 관리에서 보기 →</a>` : `<p class="preview-message">등록된 배송지가 없는 주문입니다.</p>`}
      </section>
      <section class="detail-section">
        <h4>반품 이력</h4>
        ${(orderReturns || []).length ? `<table class="detail-item-table"><thead><tr><th>사유</th><th>귀책</th><th>환불 금액</th><th>환불 상태</th><th>처리 상태</th></tr></thead>
        <tbody>${orderReturns.map(r => `<tr><td>${escapeHtml(r.reason)}</td><td><small>${r.responsibility === "SELLER_FAULT" ? "판매자 귀책" : "고객 귀책"}</small></td><td>${r.refundAmount != null ? money(r.refundAmount) : "<small>확정 전</small>"}</td>
          <td>${r.refundStatus ? `<span class="status-indicator ${REFUND_STATUS_TONE[r.refundStatus] || ""}">${REFUND_STATUS_LABEL[r.refundStatus] || r.refundStatus}</span>` : "<small>-</small>"}</td>
          <td><span class="status-indicator ${RETURN_STATUS_TONE[r.status] || ""}">${RETURN_STATUS_LABEL[r.status] || r.status}</span></td></tr>`).join("")}</tbody></table>
        <a class="text-link" href="/admin/commerce/returns">반품 관리에서 처리하기 →</a>` : `<p class="preview-message">접수된 반품이 없는 주문입니다.</p>`}
      </section>
      <section class="detail-section">
        <div class="detail-section-heading"><div><h4>처리 타임라인</h4><p>주문부터 PG·매출 원장·후속처리·정산까지 실제 연결 데이터를 표시합니다.</p></div>${trace&&trace.recoveryTasks&&trace.recoveryTasks.length?`<strong class="trace-alert">복구 작업 ${trace.recoveryTasks.length}건</strong>`:""}</div>
        <div class="op-timeline">
          ${timeline.map(step=>`<div class="op-timeline-step ${step.tone}"><time>${traceTime(step.time)}</time><i></i><div><strong>${escapeHtml(step.title)}</strong><span>${escapeHtml(step.meta||"")}</span></div></div>`).join("")}
        </div>
      </section>
      <nav class="operation-related-links" aria-label="연관 운영 화면">
        <strong>이 주문을 다른 원장에서 이어서 확인</strong>
        <div>${order.paymentId?`<a href="/admin/payment-operations?paymentId=${order.paymentId}">PG 거래</a>`:''}<a href="/admin/commerce/inventory/transactions?orderId=${order.id}">재고 이력</a><a href="/admin/payment-operations/sales-ledger?keyword=${encodeURIComponent(order.orderNo)}">매출 원장</a><a href="/admin/payment-operations/settlements">정산 관리</a><a href="/admin/payment-operations/settlements/reconciliation">PG 대사</a></div>
      </nav>`;
    if (splittable.length > 1) $("split-delivery-btn").onclick = () => splitDelivery(order.id);
  }

  async function splitDelivery(orderId) {
    const orderItemIds = [...document.querySelectorAll("[data-split-item]:checked")].map(box => Number(box.dataset.splitItem));
    if (!orderItemIds.length) return AppToast.error("분리할 상품을 먼저 선택해 주세요.");
    try {
      await apiPost(`/admin/api/commerce/orders/${orderId}/split-delivery`, { orderItemIds });
      AppToast.success("선택한 상품을 새 배송으로 분리했습니다.");
      await refresh();
      await openDetail(orderId);
    } catch (error) { AppToast.error(error.message || "부분배송 분리에 실패했습니다."); }
  }

  /* ---- 상품 정보 테이블 (Mock 생성 탭) ---- */
  async function loadProducts() {
    const data = await apiGet("/admin/api/commerce/products?saleStatus=ON_SALE&size=100");
    products = data.items || [];
  }
  function bindMockForm() {
    $("addOrderItemBtn").onclick = addRow;
    $("mockCreateBtn").onclick = createScenarioOrder;
    $("mockResetBtn").onclick = async () => {
      $("orderItemBody").innerHTML = "";
      await addRow();
      selectedScenario = "NORMAL";
      renderScenarios();
      $("mockMessage").textContent = "";
    };
  }
  async function addRow() {
    const tr = document.createElement("tr");
    tr.innerHTML = `<td class="row-category"><span class="badge-demo">${escapeHtml(products[0] && products[0].category || "미분류")}</span></td>
      <td><select class="product-select">${products.map(p => `<option value="${p.id}">${escapeHtml(p.productCode)} · ${escapeHtml(p.productName)}</option>`).join("")}</select></td>
      <td class="configuration"><span class="config-empty">불러오는 중…</span></td><td class="price">-</td>
      <td><div class="quantity-stepper"><button type="button" data-qty="minus" aria-label="Decrease quantity">&minus;</button><input class="qty" type="number" min="1" value="1"><button type="button" data-qty="plus" aria-label="Increase quantity">+</button></div></td><td class="amount">-</td>
      <td><button class="btn btn-light remove">삭제</button></td>`;
    $("orderItemBody").appendChild(tr);
    tr.querySelector(".product-select").onchange = () => configure(tr);
    tr.querySelector(".qty").oninput = preview;
    tr.querySelectorAll("[data-qty]").forEach(button => button.onclick = () => {
      const input = tr.querySelector(".qty"), delta = button.dataset.qty === "plus" ? 1 : -1;
      input.value = Math.max(1, Number(input.value || 1) + delta); preview();
    });
    tr.querySelector(".remove").onclick = () => { tr.remove(); preview(); };
    await configure(tr);
  }
  async function configure(tr) {
    const productId = Number(tr.querySelector(".product-select").value);
    const selectedProduct = products.find(x => x.id === productId);
    const categoryCell = tr.querySelector(".row-category");
    if (categoryCell) categoryCell.innerHTML = `<span class="badge-demo">${escapeHtml(selectedProduct && selectedProduct.category || "미분류")}</span>`;
    const cell = tr.querySelector(".configuration");
    cell.innerHTML = `<span class="config-empty">불러오는 중…</span>`;
    try {
      const groups = await apiGet(`/admin/api/commerce/products/${productId}/option-groups`);
      cell.innerHTML = "";
      groups.filter(g => g.exposed).forEach(g => {
        const block = document.createElement("div");
        block.className = "order-config-block";
        const available = g.values.filter(v => v.saleStatus === "ON_SALE");
        if (g.selectionType === "SINGLE") {
          block.innerHTML = `<label class="order-option-select"><span>${escapeHtml(g.groupName)}${g.requiredOption ? " *" : ""}</span><select class="option-value-select">${g.requiredOption ? "" : '<option value="" data-price="0">&#49440;&#53469; &#50504;&#54632;</option>'}${available.map((v,index) => `<option value="${v.id}" data-price="${v.additionalPrice}" ${g.requiredOption&&index===0?'selected':''}>${escapeHtml(v.valueName)}${Number(v.additionalPrice)?` (+${money(v.additionalPrice)})`:''}</option>`).join("")}</select></label>`;
        } else {
          block.innerHTML = `<strong>${escapeHtml(g.groupName)}${g.requiredOption ? " *" : ""}</strong><div>${available.map(v => `<label><input type="checkbox" name="group-${tr.rowIndex}-${g.id}" class="option-value" value="${v.id}" data-price="${v.additionalPrice}"> ${escapeHtml(v.valueName)} <small>+${money(v.additionalPrice)}</small></label>`).join("")}</div>`;
        }
        cell.appendChild(block);
      });
      if (!cell.children.length) cell.innerHTML = `<span class="config-empty">설정된 옵션 없음</span>`;
      cell.querySelectorAll("input,select").forEach(i => i.onchange = preview);
    } catch (e) { cell.textContent = "옵션을 불러오지 못했습니다."; }
    preview();
  }
  function rows() {
    return [...document.querySelectorAll("#orderItemBody tr")].map(tr => {
      const p = products.find(x => x.id === Number(tr.querySelector(".product-select").value));
      const quantity = Number(tr.querySelector(".qty").value);
      const optionValueIds = [...tr.querySelectorAll(".option-value:checked")].map(x => Number(x.value));
      tr.querySelectorAll(".option-value-select").forEach(select => { if (select.value) optionValueIds.push(Number(select.value)); });
      return { tr, p, quantity, optionValueIds };
    });
  }
  function preview() {
    let total = 0;
    rows().forEach(x => {
      if (!x.p) return;
      const checkedAmount = [...x.tr.querySelectorAll(".option-value:checked")].reduce((s, i) => s + Number(i.dataset.price), 0);
      const selectedAmount = [...x.tr.querySelectorAll(".option-value-select")].reduce((s, select) => s + Number(select.selectedOptions[0]?.dataset.price || 0), 0);
      const options = checkedAmount + selectedAmount;
      const amount = (Number(x.p.salePrice) + options) * x.quantity;
      x.tr.querySelector(".price").textContent = money(x.p.salePrice);
      x.tr.querySelector(".amount").textContent = money(amount);
      total += amount;
    });
    $("payableAmountPreview").textContent = money(total);
  }

  /* ---- 시나리오 패널 ---- */
  function renderScenarios() {
    $("scenarioList").innerHTML = SCENARIOS.map(s => `
      <label class="scenario-option ${s.value === selectedScenario ? "active" : ""}">
        <input type="radio" name="scenario" value="${s.value}" ${s.value === selectedScenario ? "checked" : ""}>
        <span>${escapeHtml(s.label)}</span>${s.recommend ? '<span class="scenario-recommend">추천</span>' : ""}
      </label>`).join("");
    document.querySelectorAll('input[name="scenario"]').forEach(input => input.onchange = () => {
      selectedScenario = input.value;
      renderScenarios();
    });
  }

  async function createScenarioOrder() {
    const button = $("mockCreateBtn");
    button.disabled = true;
    $("mockMessage").textContent = "";
    try {
      const items = rows().map(x => ({ productId: x.p && x.p.id, quantity: x.quantity, optionValueIds: x.optionValueIds, addOns: [] }));
      if (!items.length || items.some(i => !i.productId)) throw new Error("상품을 하나 이상 선택해 주세요.");
      const result = await apiPost(`/admin/api/commerce/orders/mock-scenario${activeStoreId()?`?storeId=${activeStoreId()}`:""}`, {
        scenario: selectedScenario,
        buyerName: $("buyerNameInput").value,
        buyerPhone: $("buyerPhoneInput").value,
        items
      });
      $("mockMessage").textContent = `[${result.scenarioLabel}] ${result.order.orderNo} · ${result.resultMessage}`;
      await refresh();
      openDetail(result.order.id);
    } catch (error) {
      $("mockMessage").textContent = error.message;
    } finally {
      button.disabled = false;
    }
  }

  function debounce(fn, wait) { let timer; return () => { clearTimeout(timer); timer = setTimeout(fn, wait); }; }
})();
