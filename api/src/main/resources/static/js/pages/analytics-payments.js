(function () {
  const A = window.Analytics, $ = A.$;
  A.init(render);

  async function render() {
    $("an-refresh").textContent = new Date().toLocaleString("ko-KR", { dateStyle: "short", timeStyle: "short" });
    try {
      const payments = await A.get("payments");
      renderKpi(payments);
      renderApprovalTrend(payments);
      renderMethodAmount(payments);
      renderMethodCount(payments);
    } catch (error) {
      $("an-kpis").innerHTML = `<div class="an-empty" style="grid-column:1/-1">${A.esc(error.message)}</div>`;
    }
  }

  function totals(rows) {
    return rows.reduce((s, r) => ({
      req: s.req + r.paymentRequestCount, ap: s.ap + r.approvalCount, amt: s.amt + Number(r.approvalAmount || 0),
      cancel: s.cancel + r.cancelCount, cancelAmt: s.cancelAmt + Number(r.cancelAmount || 0), fail: s.fail + r.failureCount,
    }), { req: 0, ap: 0, amt: 0, cancel: 0, cancelAmt: 0, fail: 0 });
  }

  function renderKpi(payments) {
    const t = totals(payments);
    const apRate = t.req ? (t.ap / t.req * 100) : 0;
    const cxRate = t.ap ? (t.cancel / t.ap * 100) : 0;
    $("an-kpis").innerHTML = `
      <div class="an-kpi"><span>결제 요청</span><strong>${A.num(t.req)}건</strong></div>
      <div class="an-kpi"><span>승인</span><strong>${A.num(t.ap)}건</strong><small>${A.won(t.amt)}</small></div>
      <div class="an-kpi"><span>승인율</span><strong>${apRate.toFixed(1)}%</strong></div>
      <div class="an-kpi"><span>취소</span><strong>${A.num(t.cancel)}건</strong><small>${A.won(t.cancelAmt)}</small></div>
      <div class="an-kpi"><span>취소율 / 실패</span><strong>${cxRate.toFixed(1)}%</strong><small>실패 ${A.num(t.fail)}건</small></div>`;
  }

  function byDate(rows) {
    const m = new Map();
    rows.forEach(r => {
      const a = m.get(r.date) || { req: 0, ap: 0, amt: 0 };
      a.req += r.paymentRequestCount; a.ap += r.approvalCount; a.amt += Number(r.approvalAmount || 0);
      m.set(r.date, a);
    });
    return [...m.entries()].sort((x, y) => x[0] < y[0] ? -1 : 1);
  }
  function renderApprovalTrend(payments) {
    const rows = byDate(payments);
    A.columnChart($("an-approval-trend"), rows.map(([d, a]) => ({ label: d, bar: a.amt, line: a.req ? Math.round(a.ap / a.req * 100) : 0 })), { lineUnit: "%", empty: "기간 내 결제가 없습니다." });
  }

  function byMethod(payments) {
    const m = new Map();
    payments.forEach(r => {
      const k = r.paymentMethod || "기타";
      const a = m.get(k) || { req: 0, ap: 0, amt: 0, cancel: 0, fail: 0 };
      a.req += r.paymentRequestCount; a.ap += r.approvalCount; a.amt += Number(r.approvalAmount || 0);
      a.cancel += r.cancelCount; a.fail += r.failureCount;
      m.set(k, a);
    });
    return [...m.entries()].sort((x, y) => y[1].amt - x[1].amt);
  }
  function renderMethodAmount(payments) {
    A.barChart($("an-method-amount"), byMethod(payments).map(([label, a]) => ({ label, value: a.amt })), { empty: "데이터 없음" });
  }
  function renderMethodCount(payments) {
    const rows = byMethod(payments);
    const t = totals(payments);
    const tbody = $("an-method-count").querySelector("tbody");
    tbody.innerHTML = rows.length ? rows.map(([k, a]) => `<tr>
      <td>${A.esc(k)}</td><td class="num">${A.num(a.req)}</td><td class="num">${A.num(a.ap)}</td>
      <td class="num">${A.num(a.cancel)}</td><td class="num${a.fail ? " warn" : ""}">${A.num(a.fail)}</td>
      <td class="num">${a.req ? (a.ap / a.req * 100).toFixed(1) : "0.0"}%</td></tr>`).join("")
      : `<tr><td colspan="6" class="an-empty">결제 데이터가 없습니다.</td></tr>`;
    $("an-method-count").querySelector("tfoot").innerHTML = rows.length ?
      `<tr><td>합계</td><td class="num">${A.num(t.req)}</td><td class="num">${A.num(t.ap)}</td><td class="num">${A.num(t.cancel)}</td><td class="num">${A.num(t.fail)}</td><td class="num">${t.req ? (t.ap / t.req * 100).toFixed(1) : "0.0"}%</td></tr>` : "";
  }
})();
