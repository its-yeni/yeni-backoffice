(function () {
  const $ = id => document.getElementById(id);
  const rowsEl = $("ps-rows");
  let all = [];
  let pagination;

  const today = new Date();
  $("ps-end").value = today.toISOString().slice(0, 10);
  $("ps-start").value = new Date(Date.now() - 7 * 864e5).toISOString().slice(0, 10);

  pagination = AdminPagination.mount($("ps-pagination"), { total: 0, size: 20, onChange: render });
  $("ps-search").onclick = () => { pagination.reset(); load(); };
  $("ps-keyword").addEventListener("keydown", e => { if (e.key === "Enter") { pagination.reset(); load(); } });
  $("ps-delivery").onchange = () => { pagination.reset(); render(); };
  $("ps-run-confirm").onclick = runBatchConfirm;

  function fmtDate(v) { return v ? new Date(v).toLocaleString("ko-KR", { year: "2-digit", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }) : "-"; }
  function deliveryTone(s) { return s === "배송 완료" ? "is-success" : s === "배송 중" ? "is-warning" : s === "일부 반송" ? "is-danger" : ""; }

  // 배송이 완료돼야 구매 확정이 가능하다. 그 전 단계면 진행할 화면으로 보내준다.
  function nextStep(item) {
    if (item.confirmable) return `<button class="btn btn-primary btn-sm" data-confirm="${escapeHtml(item.orderNo)}">구매 확정</button>`;
    const q = encodeURIComponent(item.orderNo);
    if (item.deliveryStatus === "배송 중") return `<a class="text-link" href="/admin/commerce/deliveries?orderNo=${q}">배송 완료 처리 →</a>`;
    return `<a class="text-link" href="/admin/commerce/shipments?keyword=${q}">출고 처리 →</a>`;
  }

  function filtered() {
    const d = $("ps-delivery").value;
    return d ? all.filter(r => r.deliveryStatus === d) : all;
  }

  function render() {
    const list = filtered();
    pagination.setTotal(list.length);
    rowsEl.innerHTML = pagination.slice(list).map(item => `<tr>
      <td>${fmtDate(item.occurredAt)}</td>
      <td><a class="text-link" href="/admin/payment-operations/sales-ledger?keyword=${encodeURIComponent(item.orderNo)}"><strong>${escapeHtml(item.orderNo)}</strong></a></td>
      <td>${escapeHtml(item.storeName || "-")}</td>
      <td><small>${escapeHtml(item.categoryNames || "미분류")}</small></td>
      <td>${escapeHtml(item.productSummary || "-")}${item.itemCount > 1 ? ` <small>(${item.itemCount}건)</small>` : ""}</td>
      <td class="amount"><strong>${money(Math.abs(Number(item.saleAmount || 0)))}</strong></td>
      <td><span class="status-indicator ${deliveryTone(item.deliveryStatus)}">${escapeHtml(item.deliveryStatus)}</span></td>
      <td class="actions">${nextStep(item)}</td></tr>`).join("");
    $("ps-empty").hidden = list.length > 0;
    rowsEl.querySelectorAll("[data-confirm]").forEach(btn => btn.onclick = () => confirmOne(btn.dataset.confirm, btn));
  }

  async function load() {
    const params = new URLSearchParams({ startDate: $("ps-start").value, endDate: $("ps-end").value });
    if ($("ps-keyword").value.trim()) params.set("keyword", $("ps-keyword").value.trim());
    const res = await apiGet("/admin/api/sales-ledger/pending?" + params.toString());
    all = res.rows || [];
    $("ps-total").textContent = money(Number(res.totalAmount || 0));
    $("ps-count").textContent = (res.count || 0).toLocaleString("ko-KR") + "건";
    $("ps-confirmable").textContent = all.filter(r => r.confirmable).length.toLocaleString("ko-KR") + "건";
    render();
  }

  async function confirmOne(orderNo, btn) {
    btn.disabled = true;
    try {
      await apiPost("/admin/api/sales-ledger/confirm", { orderNo });
      AppToast.success(`${orderNo} 매출을 구매 확정 처리했습니다.`);
      await load();
    } catch (e) {
      AppToast.error(e.message || "확정 처리에 실패했습니다.");
      btn.disabled = false;
    }
  }

  async function runBatchConfirm() {
    $("ps-run-confirm").disabled = true;
    try {
      const res = await apiPost("/admin/api/ops/run-auto-confirm", {});
      AppToast.success(res.confirmed > 0 ? `배송 완료된 ${res.confirmed}건을 구매 확정했습니다.` : "확정 대상(배송 완료 건)이 없습니다.");
      await load();
    } catch (e) {
      AppToast.error(e.message || "일괄 확정에 실패했습니다.");
    } finally {
      $("ps-run-confirm").disabled = false;
    }
  }

  load();
})();
