(function () {
  const $ = id => document.getElementById(id);
  let rows = [], stage = "", pagination;
  const drawer = $("int-detail"), backdrop = $("int-backdrop");

  function ymd(d) { return d.toISOString().slice(0, 10); }
  $("int-end").value = ymd(new Date());
  $("int-start").value = ymd(new Date(Date.now() - 7 * 864e5));

  pagination = AdminPagination.mount($("int-pagination"), { total: 0, size: 20, onChange: render });
  $("int-search-btn").onclick = load;
  $("int-keyword").addEventListener("keydown", e => { if (e.key === "Enter") load(); });
  $("int-reset").onclick = () => { $("int-keyword").value = ""; $("int-end").value = ymd(new Date()); $("int-start").value = ymd(new Date(Date.now() - 7 * 864e5)); stage = ""; load(); };
  $("int-detail-close").onclick = close; backdrop.onclick = close;

  const CHIP = { PAYMENT: "warn", FULFILL: "", CONFIRM: "", RECON: "warn", SETTLE: "", PAYOUT: "", DONE: "good" };
  const PAY_LABEL = { APPROVED: "승인", APPROVE_UNKNOWN: "결과 불명", APPROVE_FAILED: "승인 실패", FAILED: "실패", READY: "대기", CANCELLED: "취소" };
  const SET_LABEL = { NONE: "미생성", DRAFT: "초안", CONFIRM_REQUESTED: "확정 대기", CONFIRMED: "확정", PAYOUT_REQUESTED: "지급 대기", PAID: "지급 완료" };
  const LEDGER_LABEL = { NONE: "미반영", SALE: "반영", PARTIAL_CANCEL: "부분 취소", CANCEL: "취소" };
  const RECON_LABEL = { NONE: "-", MATCH: "일치", MISMATCH: "불일치" };

  function chip(text, tone) { return `<span class="int-chip ${tone || ""}">${escapeHtml(text)}</span>`; }
  function dot(text, tone) { return `<span class="status-dot ${tone || ""}">${escapeHtml(text)}</span>`; }
  function payTone(s) { return s.includes("UNKNOWN") ? "warn" : s.includes("FAIL") ? "bad" : ""; }
  function delivTone(s) { return s === "배송 완료" ? "good" : s === "배송 중" ? "warn" : s === "일부 반송" ? "bad" : ""; }

  async function load() {
    const p = new URLSearchParams({ startDate: $("int-start").value, endDate: $("int-end").value });
    if ($("int-keyword").value.trim()) p.set("keyword", $("int-keyword").value.trim());
    if (stage) p.set("stage", stage);
    const res = await apiGet("/admin/api/integrated-sales?" + p);
    rows = res.rows || [];
    $("int-count").textContent = (res.count || 0).toLocaleString("ko-KR") + "건";
    $("int-total").textContent = money(Number(res.totalAmount || 0));
    $("int-net").textContent = money(Number(res.netAmount || 0));
    renderStages(res.stageCounts || []);
    pagination.setPage(1);
    render();
  }

  function renderStages(counts) {
    const total = counts.reduce((s, c) => s + c.count, 0);
    $("int-stage-bar").innerHTML =
      `<button type="button" class="${stage ? "" : "is-on"}" data-stage="">전체 <b>${total}</b></button>` +
      counts.map(c => `<button type="button" class="${stage === c.stage ? "is-on" : ""}" data-stage="${c.stage}">${escapeHtml(c.label)} <b>${c.count}</b></button>`).join("");
    $("int-stage-bar").querySelectorAll("button").forEach(b => b.onclick = () => { stage = b.dataset.stage; load(); });
  }

  function render() {
    const list = rows;
    pagination.setTotal(list.length);
    const start = (pagination.getPage() - 1) * pagination.getSize();
    $("int-rows").innerHTML = pagination.slice(list).map(r => `<tr data-order="${r.orderId}" data-pay="${r.paymentId || ""}">
      <td><strong>${escapeHtml(r.orderNo)}</strong><br><small>${fmt(r.orderedAt)}</small></td>
      <td>${escapeHtml(r.storeName)}<br><span class="channel-badge ${r.channelType === "POS" ? "pos" : "web"}">${r.channelType === "POS" ? "매장" : "온라인"}</span></td>
      <td><span class="int-prod">${escapeHtml(r.productSummary)}${r.itemCount > 1 ? ` <small>외 ${r.itemCount - 1}</small>` : ""}</span><br><strong>${money(r.amount)}</strong>${Number(r.cancelledAmount) ? ` <small class="int-neg">−${money(r.cancelledAmount)}</small>` : ""}</td>
      <td>${dot(PAY_LABEL[r.paymentStatus] || r.paymentStatus, payTone(r.paymentStatus))}<br><small>${escapeHtml(r.cardBrief || "")}</small></td>
      <td>${dot(r.deliveryStatus, delivTone(r.deliveryStatus))}</td>
      <td>${chip(LEDGER_LABEL[r.ledgerState] || r.ledgerState, r.ledgerState === "NONE" ? "" : r.ledgerState === "SALE" ? "good" : "warn")}</td>
      <td>${r.purchaseConfirmed ? chip("확정", "good") : chip("미확정", "")}</td>
      <td>${chip(RECON_LABEL[r.reconState] || r.reconState, r.reconState === "MATCH" ? "good" : r.reconState === "MISMATCH" ? "bad" : "")}</td>
      <td>${chip(SET_LABEL[r.settlementState] || r.settlementState, r.settlementState === "PAID" ? "good" : r.settlementState.includes("REQUESTED") ? "warn" : "")}</td>
      <td class="actions">${r.nextHref ? `<a class="btn btn-primary btn-sm" href="${r.nextHref}">${escapeHtml(r.nextLabel)} →</a>` : `<span class="int-done">✓ ${escapeHtml(r.nextLabel)}</span>`}</td>
    </tr>`).join("");
    $("int-empty").hidden = list.length > 0;
    $("int-rows").querySelectorAll("tr[data-order]").forEach(tr => tr.onclick = e => {
      if (e.target.closest("a")) return;
      openDetail(Number(tr.dataset.order), tr.dataset.pay ? Number(tr.dataset.pay) : null);
    });
  }

  function fmt(v) { return v ? new Date(v).toLocaleString("ko-KR", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }) : "-"; }

  async function openDetail(orderId, paymentId) {
    const row = rows.find(r => r.orderId === orderId);
    $("int-detail-title").textContent = row ? row.orderNo : "주문";
    $("int-detail-body").innerHTML = `<p class="preview-loading">불러오는 중…</p>`;
    backdrop.hidden = false; drawer.classList.add("open");
    let trace = null;
    if (paymentId) { try { trace = await apiGet(`/admin/api/payment-operations/payments/${paymentId}/trace`); } catch (ignore) {} }
    const steps = [
      ["주문 생성", "done", row.orderNo],
      ["PG 승인", row.paymentStatus.includes("UNKNOWN") ? "current" : row.paymentStatus.includes("FAIL") ? "failed" : "done", (PAY_LABEL[row.paymentStatus] || row.paymentStatus) + " · " + (row.cardBrief || "")],
      ["출고 · 배송", row.deliveryStatus === "배송 완료" ? "done" : "current", row.deliveryStatus],
      ["매출 원장", row.ledgerState === "NONE" ? "waiting" : "done", LEDGER_LABEL[row.ledgerState] || row.ledgerState],
      ["구매 확정", row.purchaseConfirmed ? "done" : "waiting", row.purchaseConfirmed ? "확정됨" : "배송 완료 후 확정"],
      ["PG 대사", row.reconState === "MATCH" ? "done" : row.reconState === "MISMATCH" ? "failed" : "waiting", RECON_LABEL[row.reconState]],
      ["정산", row.settlementState === "PAID" ? "done" : row.settlementState === "NONE" ? "waiting" : "current", SET_LABEL[row.settlementState] || row.settlementState],
      ["지급", row.settlementState === "PAID" ? "done" : "waiting", row.settlementState === "PAID" ? "지급 완료" : "-"]
    ];
    $("int-detail-body").innerHTML = `
      <section class="drawer-summary"><strong>${money(row.amount)}</strong><p>${escapeHtml(row.storeName)} · ${row.channelType === "POS" ? "매장 POS" : "온라인"} · ${escapeHtml(row.productSummary)}</p></section>
      <section><h3>흐름</h3><ol class="int-timeline">${steps.map(([t, s, m]) => `<li class="${s}"><i></i><div><strong>${t}</strong><span>${escapeHtml(m || "")}</span></div></li>`).join("")}</ol></section>
      ${row.nextHref ? `<div class="drawer-actions"><a class="btn btn-primary" href="${row.nextHref}">${escapeHtml(row.nextLabel)} →</a></div>` : `<div class="drawer-actions"><span class="int-done">✓ ${escapeHtml(row.nextLabel)}</span></div>`}
      <section><h3>바로 가기</h3><div class="int-links">
        ${row.paymentId ? `<a href="/admin/payment-operations?paymentId=${row.paymentId}">PG 거래</a>` : ""}
        <a href="/admin/commerce/orders?orderId=${row.orderId}">주문 상세</a>
        <a href="/admin/payment-operations/sales-ledger?keyword=${encodeURIComponent(row.orderNo)}">매출 원장</a>
        <a href="/admin/commerce/deliveries?orderNo=${encodeURIComponent(row.orderNo)}">배송</a>
        ${row.settlementStatementId ? `<a href="/admin/payment-operations/settlements?statementId=${row.settlementStatementId}">정산 명세</a>` : ""}
      </div></section>`;
  }
  function close() { drawer.classList.remove("open"); backdrop.hidden = true; }

  const q = new URLSearchParams(location.search);
  if (q.get("keyword")) $("int-keyword").value = q.get("keyword");
  if (q.get("stage")) stage = q.get("stage");
  load();
})();
