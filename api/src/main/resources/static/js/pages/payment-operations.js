(function () {
  let payments = [], activeTab = "", pagination;
  const $ = id => document.getElementById(id);
  const rows = $("payment-rows"), drawer = $("payment-detail"), backdrop = $("transaction-backdrop");
  const STATUS_LABEL = {APPROVED:"승인", APPROVE_UNKNOWN:"결과 불명", APPROVE_FAILED:"승인 실패", PARTIAL_CANCELED:"부분 취소", CANCELED:"취소 완료", CANCEL_UNKNOWN:"취소 결과 불명"};

  pagination = AdminPagination.mount($("payment-pagination"), {total:0, size:20, onChange:render});
  setDefaultDates();
  const requestedStatus = new URLSearchParams(location.search).get("status");
  if (requestedStatus === "UNKNOWN") activeTab = "APPROVE_UNKNOWN,CANCEL_UNKNOWN,UNKNOWN,CANCEL_RECONCILE_REQUIRED,NETWORK_CANCEL_REQUIRED";
  else if (requestedStatus) activeTab = requestedStatus;
  if (requestedStatus && !activeTab.includes(",")) $("payment-status").value = requestedStatus;
  const requestedKeyword = new URLSearchParams(location.search).get("keyword");
  if (requestedKeyword) $("payment-keyword").value = requestedKeyword;
  $("payment-refresh").onclick = resetFilters;
  $("payment-status").onchange = () => { activeTab = $("payment-status").value; syncTabs(); resetAndRender(); };
  $("payment-keyword").oninput = resetAndRender;
  $("payment-start").onchange = resetAndRender;
  $("payment-end").onchange = resetAndRender;
  $("payment-unassigned").onchange = load;
  $("payment-detail-close").onclick = close;
  backdrop.onclick = close;
  document.querySelectorAll("#payment-status-tabs [data-status]").forEach(button => button.onclick = () => {
    activeTab = button.dataset.status;
    $("payment-status").value = activeTab.includes(",") ? "" : activeTab;
    syncTabs(); resetAndRender();
  });
  syncTabs();

  function statusLabel(status) { return STATUS_LABEL[status] || status || "-"; }
  function statusClass(status) { return String(status).includes("UNKNOWN") ? "warning" : status === "APPROVED" ? "success" : String(status).includes("FAILED") || status === "CANCELED" ? "danger" : "neutral"; }
  function paymentDate(item) { return item.approvedAt || item.createdAt || item.updatedAt; }
  function formatDate(value) { return value ? new Date(value).toLocaleString("ko-KR", {year:"numeric",month:"2-digit",day:"2-digit",hour:"2-digit",minute:"2-digit"}) : "-"; }
  function resetAndRender() { pagination.setPage(1); render(); }

  function filteredPayments() {
    const keyword = $("payment-keyword").value.trim().toLowerCase();
    const start = $("payment-start").value ? new Date($("payment-start").value + "T00:00:00") : null;
    const end = $("payment-end").value ? new Date($("payment-end").value + "T23:59:59.999") : null;
    const statuses = activeTab ? activeTab.split(",") : [];
    return payments.filter(item => {
      const occurredAt = paymentDate(item) ? new Date(paymentDate(item)) : null;
      if (statuses.length && !statuses.includes(item.paymentStatus)) return false;
      if (start && occurredAt && occurredAt < start) return false;
      if (end && occurredAt && occurredAt > end) return false;
      return !keyword || [item.orderNo, item.productName, item.id, item.tid].some(value => String(value || "").toLowerCase().includes(keyword));
    });
  }

  function render() {
    const visible = filteredPayments();
    pagination.setTotal(visible.length);
    const start = (pagination.getPage() - 1) * pagination.getSize();
    rows.innerHTML = visible.slice(start, start + pagination.getSize()).map((item, i) => `<tr data-payment="${item.id}" tabindex="0">
      <td class="row-index">${start + i + 1}</td>
      <td>${formatDate(paymentDate(item))}</td><td><strong>${escapeHtml(item.orderNo)}</strong></td><td>${escapeHtml(operationalStoreLabel(item.storeId))}</td><td>${escapeHtml(item.productName || "상품 정보 없음")}</td><td class="mono">#${item.id}</td><td class="mono">${copyableValue(item.tid)}</td>
      <td class="amount">${Number(item.approvedAmount || 0) ? money(item.approvedAmount) : "-"}</td><td class="amount">${Number(item.canceledAmount || 0) ? money(item.canceledAmount) : "-"}</td>
      <td><span class="transaction-status ${statusClass(item.paymentStatus)}">${statusLabel(item.paymentStatus)}</span></td><td class="actions"><button class="btn btn-light btn-sm" type="button">상세</button></td></tr>`).join("");
    $("payment-empty").hidden = visible.length > 0;
    $("payment-result-count").textContent = `조회 ${visible.length.toLocaleString("ko-KR")}건`;
    rows.querySelectorAll("[data-payment]").forEach(row => {
      row.onclick = event => { if (!event.target.closest(".copy-chip")) open(Number(row.dataset.payment)); };
      row.onkeydown = event => { if (event.key === "Enter") row.click(); };
    });
  }

  function renderMetrics() {
    const count = status => payments.filter(item => status.split(",").includes(item.paymentStatus)).length;
    $("metric-all").textContent = `${payments.length.toLocaleString("ko-KR")}건`;
    $("metric-approved").textContent = `${count("APPROVED").toLocaleString("ko-KR")}건`;
    $("metric-partial").textContent = `${count("PARTIAL_CANCELED").toLocaleString("ko-KR")}건`;
    $("metric-canceled").textContent = `${count("CANCELED").toLocaleString("ko-KR")}건`;
    $("metric-unknown").textContent = `${count("APPROVE_UNKNOWN,CANCEL_UNKNOWN").toLocaleString("ko-KR")}건`;
    $("metric-failed").textContent = `${count("APPROVE_FAILED").toLocaleString("ko-KR")}건`;
    $("metric-approved-amount").textContent = money(payments.filter(item => item.paymentStatus === "APPROVED").reduce((sum, item) => sum + Number(item.approvedAmount || 0), 0));
  }

  function syncTabs() {
    document.querySelectorAll("#payment-status-tabs [data-status]").forEach(button => button.classList.toggle("active", button.dataset.status === activeTab));
  }

  function step(state, title, description, link) {
    const content = `<b>${title}</b><span>${description}</span>`;
    return `<li class="${state}">${link ? `<a href="${link}">${content}<em>열기 →</em></a>` : content}</li>`;
  }

  async function open(id) {
    const trace = await apiGet(`/admin/api/payment-operations/payments/${id}/trace`), payment = trace.payment;
    const sales = trace.sales || [], settlementDetails = trace.settlementDetails || [];
    const unknown = payment.paymentStatus.includes("UNKNOWN"), failed = payment.paymentStatus.includes("FAILED");
    const ledgerLink = sales.length ? `/admin/payment-operations/sales-ledger?ledgerId=${sales[0].id}` : null;
    const settlementLink = settlementDetails.length ? `/admin/payment-operations/settlements?statementId=${settlementDetails[0].settlementStatementId}` : "/admin/payment-operations/settlements";
    $("payment-detail-title").textContent = payment.orderNo;
    $("payment-detail-body").innerHTML = `<section class="drawer-summary"><span class="transaction-status ${statusClass(payment.paymentStatus)}">${statusLabel(payment.paymentStatus)}</span><strong>${money(payment.approvedAmount)}</strong><p>결제 ID #${payment.id} · ${escapeHtml(payment.tid || "TID 미확정")}</p></section>
      ${paymentMethodSection(payment)}
      <section><div class="drawer-section-heading"><h3>처리 흐름</h3><small>한 거래의 후속 처리를 단계별로 추적합니다.</small></div><ol class="trace-list operational-trace">${step("done","1. 주문 생성",payment.orderNo,null)}${step(unknown?"current":failed?"failed":"done","2. PG 승인",statusLabel(payment.paymentStatus),null)}${step(sales.length?"done":"waiting","3. 매출 원장",sales.length?`${sales.length}건 반영됨`:failed?"승인 실패로 미생성":"반영 대기",ledgerLink)}${step(settlementDetails.length?"done":"waiting","4. 정산",settlementDetails.length?"정산 명세 포함":"정산 배치 대기",settlementLink)}</ol></section>
      <section><h3>운영 처리 현황</h3><table class="drawer-operation-table"><tbody><tr><th>PG 로그</th><td>${trace.pgLogs.length}건</td><th>복구 작업</th><td>${trace.recoveryTasks.length}건</td></tr><tr><th>외부 전송</th><td>${trace.externalSends.length}건</td><th>알림톡</th><td>${trace.alimtalkQueues.length}건</td></tr></tbody></table></section>
      <div class="drawer-actions">${unknown?'<button class="btn btn-primary" id="retry-payment">PG 결과 다시 조회</button>':""}<a class="btn btn-light" href="${ledgerLink || `/admin/payment-operations/sales-ledger?keyword=${encodeURIComponent(payment.orderNo)}`}">매출 원장 확인</a><a class="btn btn-light" href="${settlementLink}">정산 관리 확인</a></div>`;
    if (payment.storeId == null && activeStoreId()) {
      const assign = document.createElement("button"); assign.className = "btn btn-primary"; assign.textContent = "현재 매장으로 지정";
      assign.onclick = async () => { if (!confirm("이 거래를 현재 매장에 연결할까요?")) return; await apiPost(`/admin/api/payment-operations/payments/${payment.id}/store/${activeStoreId()}`, {}, "POST"); await load(); close(); };
      $("payment-detail-body").querySelector(".drawer-actions")?.prepend(assign);
    }
    if (unknown) $("retry-payment").onclick = async () => { await apiPost(`/admin/api/payment-operations/payments/${payment.id}/retry-query`, {}, "POST"); await load(); await open(payment.id); };
    backdrop.hidden = false; drawer.classList.add("open");
  }

  const ACQ_LABEL = { APPROVED: "승인 (매입 전)", ACQUIRED: "매입 완료", UNSETTLED: "미매입", "N/A": "해당 없음" };
  function paymentMethodSection(p) {
    const cash = (p.paymentMethod || "") === "CASH";
    const channel = p.channelType === "POS" ? "매장 POS" : "온라인";
    const rows = [];
    rows.push(["결제 채널", channel + (cash ? " · 현금" : "")]);
    if (cash) {
      rows.push(["결제수단", "현금"]);
    } else {
      rows.push(["결제수단", `${escapeHtml(p.issuerName || "카드")} ${p.cardLast4 ? "•••• " + escapeHtml(p.cardLast4) : ""}`.trim()]);
      rows.push(["할부", Number(p.installmentMonths) ? `${p.installmentMonths}개월` : "일시불"]);
      rows.push(["승인번호", p.approvalNo ? escapeHtml(p.approvalNo) : "-"]);
      rows.push(["매입 상태", ACQ_LABEL[p.acquiringStatus] || p.acquiringStatus || "-"]);
    }
    rows.push(["정산 예정일", p.settlementDueDate ? escapeHtml(p.settlementDueDate) : "-"]);
    return `<section><h3>결제 수단</h3><table class="drawer-operation-table drawer-kv"><tbody>${
      rows.map(([k, v]) => `<tr><th>${k}</th><td>${v}</td></tr>`).join("")}</tbody></table></section>`;
  }

  function close() { drawer.classList.remove("open"); backdrop.hidden = true; }
  // 목록 화면 날짜 필터 기본값은 "오늘"으로 통일(common.js initializeDatePresets 와 동일 규칙).
  function setDefaultDates() { const now = new Date(); $("payment-start").value = localDate(new Date(now.getTime() - 6 * 864e5)); $("payment-end").value = localDate(now); }
  function localDate(date) { const offset = date.getTimezoneOffset() * 60000; return new Date(date.getTime() - offset).toISOString().slice(0, 10); }
  function resetFilters() { $("payment-keyword").value = ""; $("payment-status").value = ""; activeTab = ""; setDefaultDates(); syncTabs(); resetAndRender(); }
  async function load() {
    payments = await apiGet(`/admin/api/payment-operations/payments?includeUnassigned=${$("payment-unassigned").checked}`);
    renderMetrics(); render();
    const requested = Number(new URLSearchParams(location.search).get("paymentId"));
    if (requested && payments.some(item => item.id === requested)) open(requested);
  }
  load();
})();
