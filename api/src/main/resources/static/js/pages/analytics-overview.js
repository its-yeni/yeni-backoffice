(function () {
  const A = window.Analytics, $ = A.$;

  A.init(render);

  async function render() {
    $("an-refresh").textContent = new Date().toLocaleString("ko-KR", { dateStyle: "short", timeStyle: "short" });
    try {
      const [kpi, orders, payments, settlements, inventory] = await Promise.all([
        A.get("overview"), A.get("orders"), A.get("payments"), A.get("settlements"), A.get("inventory"),
      ]);
      renderKpi(kpi);
      renderTrend(orders);
      renderChannel(orders);
      renderMethodTable(payments);
      renderMismatch(settlements);
      renderLowStock(inventory);
    } catch (error) {
      $("an-kpis").innerHTML = `<div class="an-empty" style="grid-column:1/-1">${A.esc(error.message)}</div>`;
    }
  }

  function delta(rate) {
    if (rate === 0 || rate == null) return "";
    const cls = rate > 0 ? "up" : "down";
    return `<small class="${cls}">이전 대비 ${rate > 0 ? "+" : ""}${Number(rate).toFixed(1)}%</small>`;
  }

  function renderKpi(k) {
    $("an-kpis").innerHTML = `
      <div class="an-kpi"><span>총 주문</span><strong>${A.num(k.orderCount)}건</strong>${delta(k.orderCountChangeRate)}</div>
      <div class="an-kpi"><span>총 주문금액</span><strong>${A.won(k.orderAmount)}</strong>${delta(k.orderAmountChangeRate)}</div>
      <div class="an-kpi"><span>평균 주문금액</span><strong>${A.won(k.averageOrderAmount)}</strong>${delta(k.averageOrderAmountChangeRate)}</div>
      <div class="an-kpi"><span>결제 승인율</span><strong>${A.pct(k.paymentApprovalRate)}</strong>${delta(k.paymentApprovalRateChange)}</div>
      <div class="an-kpi"><span>정산 차이</span><strong>${A.won(k.settlementDifferenceAmount)}</strong><small>불일치 ${A.num(k.settlementMismatchCount)}건</small></div>`;
  }

  function byDate(rows, valueFn) {
    const m = new Map();
    rows.forEach(r => { const v = m.get(r.date) || 0; m.set(r.date, v + valueFn(r)); });
    return [...m.entries()].sort((a, b) => a[0] < b[0] ? -1 : 1);
  }

  function renderTrend(orders) {
    const amount = byDate(orders, r => Number(r.orderAmount || 0));
    const count = new Map(byDate(orders, r => Number(r.orderCount || 0)));
    A.columnChart($("an-trend"), amount.map(([d, a]) => ({ label: d, bar: a, line: count.get(d) || 0 })), { lineUnit: "건", empty: "기간 내 주문이 없습니다." });
  }

  function renderChannel(orders) {
    const m = new Map();
    orders.forEach(r => m.set(r.channel || "기타", (m.get(r.channel || "기타") || 0) + Number(r.orderAmount || 0)));
    A.barChart($("an-channel-chart"), [...m.entries()].sort((a, b) => b[1] - a[1]).map(([label, value]) => ({ label, value })), { empty: "데이터 없음" });
  }

  function renderMethodTable(payments) {
    const m = new Map();
    payments.forEach(r => {
      const k = r.paymentMethod || "기타";
      const acc = m.get(k) || { req: 0, ap: 0, cancel: 0, amt: 0 };
      acc.req += r.paymentRequestCount; acc.ap += r.approvalCount; acc.cancel += r.cancelCount; acc.amt += Number(r.approvalAmount || 0);
      m.set(k, acc);
    });
    const rows = [...m.entries()].sort((a, b) => b[1].amt - a[1].amt);
    const tot = rows.reduce((s, [, a]) => ({ req: s.req + a.req, ap: s.ap + a.ap, cancel: s.cancel + a.cancel, amt: s.amt + a.amt }), { req: 0, ap: 0, cancel: 0, amt: 0 });
    const t = $("an-method-table");
    t.querySelector("tbody").innerHTML = rows.length ? rows.map(([k, a]) => `<tr>
      <td>${A.esc(k)}</td><td class="num">${A.num(a.req)}</td><td class="num">${A.num(a.ap)}</td><td class="num">${A.num(a.cancel)}</td>
      <td class="num">${a.req ? (a.ap / a.req * 100).toFixed(1) : "0.0"}%</td><td class="num">${A.won(a.amt)}</td></tr>`).join("")
      : `<tr><td colspan="6" class="an-empty">결제 데이터가 없습니다.</td></tr>`;
    t.querySelector("tfoot").innerHTML = rows.length ? `<tr><td>합계</td><td class="num">${A.num(tot.req)}</td><td class="num">${A.num(tot.ap)}</td><td class="num">${A.num(tot.cancel)}</td><td class="num">${tot.req ? (tot.ap / tot.req * 100).toFixed(1) : "0.0"}%</td><td class="num">${A.won(tot.amt)}</td></tr>` : "";
  }

  function renderMismatch(settlements) {
    const rows = settlements.filter(r => r.isMismatch).slice(0, 5);
    $("an-mismatch-table").querySelector("tbody").innerHTML = rows.length ? rows.map(r => `<tr>
      <td>${A.esc(r.orderNo || "-")}</td><td>${A.esc(r.storeName || "-")}</td>
      <td class="num">${A.won(r.approvalAmount)}</td><td class="num">${A.won(r.settlementAmount)}</td>
      <td class="num neg">${A.won(r.differenceAmount)}</td><td>${A.esc(r.settlementDate || "-")}</td></tr>`).join("")
      : `<tr><td colspan="6" class="an-empty">정산 불일치가 없습니다.</td></tr>`;
  }

  function renderLowStock(inventory) {
    const rows = inventory.filter(r => r.isLowStock).slice(0, 5);
    $("an-lowstock-table").querySelector("tbody").innerHTML = rows.length ? rows.map(r => `<tr>
      <td>${A.esc(r.productName)}${r.optionName && r.optionName !== "기본" ? " · " + A.esc(r.optionName) : ""}</td>
      <td class="num warn">${A.num(r.currentStock)}</td><td class="num">${A.num(r.safetyStock)}</td><td class="num">${A.num(r.soldLast30Days)}</td></tr>`).join("")
      : `<tr><td colspan="4" class="an-empty">재고 부족 상품이 없습니다.</td></tr>`;
  }
})();
