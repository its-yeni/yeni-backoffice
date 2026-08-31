(function () {
  const A = window.Analytics, $ = A.$;
  let detailRows = [], detailPager = null;
  A.init(render);

  async function render() {
    $("an-refresh").textContent = new Date().toLocaleString("ko-KR", { dateStyle: "short", timeStyle: "short" });
    try {
      const [settlements, payments] = await Promise.all([A.get("settlements"), A.get("payments")]);
      renderKpi(settlements, payments);
      renderDaily(settlements);
      renderStoreDiff(settlements);
      renderMethodAmount(settlements);
      renderDetail(settlements);
    } catch (error) {
      $("an-kpis").innerHTML = `<div class="an-empty" style="grid-column:1/-1">${A.esc(error.message)}</div>`;
    }
  }

  function renderKpi(settlements, payments) {
    const approvalAmt = payments.reduce((s, r) => s + Number(r.approvalAmount || 0), 0);
    const cancelAmt = payments.reduce((s, r) => s + Number(r.cancelAmount || 0), 0);
    const scheduled = settlements.filter(r => r.settlementStatus === "DRAFT" || r.settlementStatus === "CONFIRMED")
      .reduce((s, r) => s + Number(r.settlementAmount || 0), 0);
    const paid = settlements.filter(r => r.settlementStatus === "PAID").reduce((s, r) => s + Number(r.settlementAmount || 0), 0);
    const diff = settlements.filter(r => r.isMismatch).reduce((s, r) => s + Math.abs(Number(r.differenceAmount || 0)), 0);
    const mismatchCount = settlements.filter(r => r.isMismatch).length;
    $("an-kpis").innerHTML = `
      <div class="an-kpi"><span>결제 승인금액</span><strong>${A.won(approvalAmt)}</strong></div>
      <div class="an-kpi"><span>결제 취소금액</span><strong>${A.won(cancelAmt)}</strong></div>
      <div class="an-kpi"><span>정산 예정금액</span><strong>${A.won(scheduled)}</strong></div>
      <div class="an-kpi"><span>정산 완료금액</span><strong>${A.won(paid)}</strong></div>
      <div class="an-kpi"><span>정산 차이금액</span><strong>${A.won(diff)}</strong></div>
      <div class="an-kpi"><span>정산 불일치</span><strong class="${mismatchCount ? "" : ""}">${A.num(mismatchCount)}건</strong></div>`;
  }

  function renderDaily(settlements) {
    const m = new Map();
    settlements.forEach(r => {
      const a = m.get(r.settlementDate) || { approval: 0, settle: 0 };
      a.approval += Number(r.approvalAmount || 0); a.settle += Number(r.settlementAmount || 0);
      m.set(r.settlementDate, a);
    });
    const rows = [...m.entries()].sort((x, y) => x[0] < y[0] ? -1 : 1);
    A.columnChart($("an-daily"), rows.map(([d, a]) => ({ label: d, bar: a.approval, line: a.settle })), { lineUnit: "원", empty: "기간 내 정산 데이터가 없습니다." });
  }

  function renderStoreDiff(settlements) {
    const m = new Map();
    settlements.forEach(r => {
      const k = r.storeName || "-";
      m.set(k, (m.get(k) || 0) + Math.abs(Number(r.differenceAmount || 0)));
    });
    A.barChart($("an-store-diff"), [...m.entries()].filter(([, v]) => v > 0).sort((a, b) => b[1] - a[1]).map(([label, value]) => ({ label, value })), { empty: "정산 차이 없음" });
  }

  function renderMethodAmount(settlements) {
    const m = new Map();
    settlements.forEach(r => m.set(r.paymentMethod || "기타", (m.get(r.paymentMethod || "기타") || 0) + Number(r.settlementAmount || 0)));
    A.barChart($("an-method-amount"), [...m.entries()].sort((a, b) => b[1] - a[1]).map(([label, value]) => ({ label, value })), { empty: "데이터 없음" });
  }

  function renderDetail(settlements) {
    detailRows = [...settlements].sort((a, b) => (b.isMismatch - a.isMismatch) || (a.settlementDate < b.settlementDate ? 1 : -1));
    const mismatch = settlements.filter(r => r.isMismatch).length;
    $("an-mismatch-summary").textContent = `총 ${settlements.length}건 · 불일치 ${mismatch}건`;
    if (!detailPager && window.AdminPagination && $("an-detail-pagination")) {
      detailPager = AdminPagination.mount($("an-detail-pagination"), { inline: true, size: 20, total: detailRows.length, onChange: paintDetailRows });
    }
    if (detailPager) detailPager.setTotal(detailRows.length);
    paintDetailRows();
  }

  function paintDetailRows() {
    const page = detailPager ? detailPager.slice(detailRows) : detailRows;
    $("an-detail-table").querySelector("tbody").innerHTML = detailRows.length ? page.map(r => `<tr>
      <td>${A.esc(r.settlementDate || "-")}</td><td>${A.esc(r.orderNo || "-")}</td>
      <td style="font-family:monospace;font-size:11px">${A.esc((r.tid || "-").slice(0, 22))}</td>
      <td>${A.esc(r.storeName || "-")}</td><td>${A.esc(r.paymentMethod || "-")}</td>
      <td class="num">${A.won(r.approvalAmount)}</td><td class="num">${A.won(r.settlementAmount)}</td>
      <td class="num${r.isMismatch ? " neg" : ""}">${A.won(r.differenceAmount)}</td>
      <td>${A.esc(r.settlementStatus || "-")}</td>
      <td>${r.isMismatch ? '<span class="warn">불일치</span>' : "일치"}</td></tr>`).join("")
      : `<tr><td colspan="10" class="an-empty">정산 대사 데이터가 없습니다.</td></tr>`;
  }
})();
