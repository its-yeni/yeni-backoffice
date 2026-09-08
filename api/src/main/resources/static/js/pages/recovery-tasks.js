(function () {
  const $ = id => document.getElementById(id);
  const rowsEl = $("rt-rows");
  let all = [];
  let pagination;

  const STATUS = {
    READY: ["재시도 대기", "warning"], PROCESSING: ["처리 중", "neutral"],
    SUCCESS: ["완료", "success"], FAILED: ["실패", "danger"]
  };
  const TYPE = {
    APPROVE_UNKNOWN_CHECK: "승인 결과불명 재조회", CANCEL_UNKNOWN_CHECK: "취소 결과불명 재조회",
    NETWORK_CANCEL: "망취소", APPROVE_INTERNAL_SAVE_FAILED: "승인 내부저장 실패",
    EXTERNAL_SEND_RETRY: "외부전송 재시도", ALIMTALK_RETRY: "알림톡 재시도"
  };
  const RETRYABLE = new Set(["READY", "FAILED"]);

  const today = new Date();
  $("rt-end").value = today.toISOString().slice(0, 10);
  $("rt-start").value = new Date(today.getTime() - 7 * 864e5).toISOString().slice(0, 10);

  const params0 = new URLSearchParams(location.search);
  if (params0.get("keyword")) $("rt-keyword").value = params0.get("keyword");
  if (params0.get("status") && STATUS[params0.get("status")]) $("rt-status").value = params0.get("status");
  if (params0.get("type") && TYPE[params0.get("type")]) $("rt-type").value = params0.get("type");

  pagination = AdminPagination.mount($("rt-pagination"), { total: 0, size: 20, onChange: render });
  $("rt-search").onclick = () => { pagination.reset(); load(); };
  $("rt-keyword").addEventListener("keydown", e => { if (e.key === "Enter") { pagination.reset(); load(); } });
  $("rt-status").onchange = () => { pagination.reset(); render(); };
  $("rt-type").onchange = () => { pagination.reset(); render(); };
  $("rt-drawer-close").onclick = closeDrawer;
  $("rt-backdrop").onclick = closeDrawer;

  function fmt(v) { return v ? new Date(v).toLocaleString("ko-KR", { year: "2-digit", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }) : "-"; }
  function statusChip(s) { const [label, tone] = STATUS[s] || [s, "neutral"]; return `<span class="transaction-status ${tone}">${label}</span>`; }

  function filtered() {
    const s = $("rt-status").value, t = $("rt-type").value;
    return all.filter(r => (!s || r.status === s) && (!t || r.recoveryType === t));
  }

  function render() {
    const list = filtered();
    pagination.setTotal(list.length);
    rowsEl.innerHTML = pagination.slice(list).map(item => `<tr>
      <td>${fmt(item.createdAt)}</td>
      <td><button class="rt-key" data-detail="${item.id}">${escapeHtml(item.taskKey)}</button></td>
      <td>${item.orderNo ? `<a class="text-link" href="/admin/payment-operations?keyword=${encodeURIComponent(item.orderNo)}">${escapeHtml(item.orderNo)}</a>` : "-"}</td>
      <td><small>${escapeHtml(TYPE[item.recoveryType] || item.recoveryType)}</small></td>
      <td>${statusChip(item.status)}</td>
      <td class="amount">${item.retryCount ?? 0}/${item.maxRetryCount ?? 0}</td>
      <td><small class="rt-error">${escapeHtml(item.lastErrorMessage || "-")}</small></td>
      <td class="actions"><button class="btn btn-light btn-sm" data-detail="${item.id}">상세</button></td></tr>`).join("");
    $("rt-empty").hidden = list.length > 0;
    rowsEl.querySelectorAll("[data-detail]").forEach(b => b.onclick = () => openDrawer(Number(b.dataset.detail)));
  }

  function updateSummary() {
    const by = s => all.filter(r => r.status === s).length;
    $("rt-total").textContent = all.length.toLocaleString("ko-KR") + "건";
    $("rt-ready").textContent = by("READY").toLocaleString("ko-KR") + "건";
    $("rt-failed").textContent = by("FAILED").toLocaleString("ko-KR") + "건";
    $("rt-success").textContent = by("SUCCESS").toLocaleString("ko-KR") + "건";
  }

  async function load() {
    const params = new URLSearchParams({ startDate: $("rt-start").value, endDate: $("rt-end").value, size: "100" });
    if ($("rt-keyword").value.trim()) params.set("keyword", $("rt-keyword").value.trim());
    const res = await apiGet("/admin/api/recovery/tasks?" + params.toString());
    all = res.data || [];
    updateSummary();
    render();
  }

  function drawerRow(dt, dd) { return dd == null || dd === "" ? "" : `<div><dt>${dt}</dt><dd>${escapeHtml(String(dd))}</dd></div>`; }

  async function openDrawer(id) {
    const task = all.find(r => r.id === id) || await apiGet(`/admin/api/recovery/tasks/${id}`);
    $("rt-drawer-title").textContent = task.taskKey;
    const retryable = RETRYABLE.has(task.status);
    $("rt-drawer-body").innerHTML = `
      <section class="drawer-summary">${statusChip(task.status)}<strong>${escapeHtml(TYPE[task.recoveryType] || task.recoveryType)}</strong><p>${escapeHtml(task.taskKey)}</p></section>
      <section><h3>추적 정보</h3><dl class="drawer-meta">
        ${drawerRow("주문번호", task.orderNo)}
        ${drawerRow("결제 ID", task.paymentId ? "#" + task.paymentId : null)}
        ${drawerRow("취소 ID", task.cancelId ? "#" + task.cancelId : null)}
        ${drawerRow("TID", task.tid)}
        ${drawerRow("멱등키", task.idempotencyKey)}
        ${drawerRow("재시도", `${task.retryCount ?? 0} / ${task.maxRetryCount ?? 0}`)}
        ${drawerRow("마지막 시도", fmt(task.lastTriedAt))}
        ${drawerRow("처리 완료", fmt(task.processedAt))}
        ${drawerRow("생성", fmt(task.createdAt))}
      </dl></section>
      ${task.lastErrorMessage ? `<section><h3>마지막 오류</h3><p class="rt-error-full">${escapeHtml(task.lastErrorMessage)}</p></section>` : ""}
      <section><h3>처리</h3><div class="drawer-actions">
        ${retryable ? `<button class="btn btn-primary btn-sm" data-act="retry">재시도</button>` : ""}
        ${task.status !== "SUCCESS" ? `<button class="btn btn-light btn-sm" data-act="mark-success">성공 처리</button>` : ""}
        ${task.status !== "FAILED" ? `<button class="btn btn-light btn-sm" data-act="mark-failed">실패 처리</button>` : ""}
        ${task.orderNo ? `<a class="btn btn-light btn-sm" href="/admin/payment-operations?keyword=${encodeURIComponent(task.orderNo)}">PG 거래 확인</a>` : ""}
      </div><p class="rt-act-note">재시도는 READY/FAILED 상태만 가능하며, 결과불명 재조회 유형만 실제 PG 재조회를 수행합니다. 나머지 유형은 성공/실패를 운영자가 확인 처리합니다.</p></section>`;
    $("rt-drawer-body").querySelectorAll("[data-act]").forEach(b => b.onclick = () => act(id, b.dataset.act, b));
    $("rt-backdrop").hidden = false;
    $("rt-drawer").classList.add("open");
  }

  async function act(id, action, btn) {
    btn.disabled = true;
    try {
      await apiPost(`/admin/api/recovery/tasks/${id}/${action}`, {}, "POST");
      AppToast.success(action === "retry" ? "재시도를 요청했습니다." : "상태를 갱신했습니다.");
      await load();
      await openDrawer(id);
    } catch (e) {
      AppToast.error(e.message || "처리에 실패했습니다.");
      btn.disabled = false;
    }
  }

  function closeDrawer() { $("rt-drawer").classList.remove("open"); $("rt-backdrop").hidden = true; }

  load();
})();
