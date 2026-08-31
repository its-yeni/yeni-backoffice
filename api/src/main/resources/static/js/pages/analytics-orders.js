(function () {
  const A = window.Analytics, $ = A.$;
  A.init(render);

  async function render() {
    $("an-refresh").textContent = new Date().toLocaleString("ko-KR", { dateStyle: "short", timeStyle: "short" });
    const p = new URLSearchParams();
    if ($("an-start").value) p.set("startDate", $("an-start").value);
    if ($("an-end").value) p.set("endDate", $("an-end").value);
    try {
      const [orders, breakdown] = await Promise.all([
        A.get("orders"),
        A.fetchJson("/admin/api/sales-analytics/breakdown" + (p.toString() ? "?" + p : "")).catch(() => ({ products: [] })),
      ]);
      renderKpi(orders);
      renderTrend(orders);
      renderChannel(orders);
      renderStore(orders);
      renderProducts(breakdown.products || []);
    } catch (error) {
      $("an-kpis").innerHTML = `<div class="an-empty" style="grid-column:1/-1">${A.esc(error.message)}</div>`;
    }
  }

  function renderKpi(orders) {
    const count = orders.reduce((s, r) => s + Number(r.orderCount || 0), 0);
    const amount = orders.reduce((s, r) => s + Number(r.orderAmount || 0), 0);
    const cancelCount = orders.reduce((s, r) => s + Number(r.cancelCount || 0), 0);
    const avg = count ? Math.round(amount / count) : 0;
    $("an-kpis").innerHTML = `
      <div class="an-kpi"><span>총 주문건수</span><strong>${A.num(count)}건</strong></div>
      <div class="an-kpi"><span>총 주문금액</span><strong>${A.won(amount)}</strong></div>
      <div class="an-kpi"><span>평균 주문금액</span><strong>${A.won(avg)}</strong></div>
      <div class="an-kpi"><span>취소건수</span><strong>${A.num(cancelCount)}건</strong></div>`;
  }

  function byDate(rows, fn) {
    const m = new Map();
    rows.forEach(r => m.set(r.date, (m.get(r.date) || 0) + fn(r)));
    return [...m.entries()].sort((a, b) => a[0] < b[0] ? -1 : 1);
  }
  function renderTrend(orders) {
    const amt = byDate(orders, r => Number(r.orderAmount || 0));
    const cnt = new Map(byDate(orders, r => Number(r.orderCount || 0)));
    A.columnChart($("an-trend"), amt.map(([d, a]) => ({ label: d, bar: a, line: cnt.get(d) || 0 })), { lineUnit: "건", empty: "기간 내 주문이 없습니다." });
  }
  function groupBar(el, orders, keyFn, limit) {
    const m = new Map();
    orders.forEach(r => { const k = keyFn(r) || "기타"; m.set(k, (m.get(k) || 0) + Number(r.orderAmount || 0)); });
    let rows = [...m.entries()].sort((a, b) => b[1] - a[1]);
    if (limit) rows = rows.slice(0, limit);
    A.barChart(el, rows.map(([label, value]) => ({ label, value })), { empty: "데이터 없음" });
  }
  function renderChannel(orders) { groupBar($("an-channel-chart"), orders, r => r.channel); }
  function renderStore(orders) { groupBar($("an-store-chart"), orders, r => r.storeName, 10); }

  function renderProducts(products) {
    const rows = [...products].sort((a, b) => Number(b.netAmount || 0) - Number(a.netAmount || 0)).slice(0, 10);
    $("an-product-table").querySelector("tbody").innerHTML = rows.length ? rows.map(r => `<tr>
      <td>${A.esc(r.productName || "-")}</td><td class="num">${A.num(r.quantity)}</td>
      <td class="num">${A.won(r.netAmount)}</td><td class="num">${A.num(r.lineCount)}</td></tr>`).join("")
      : `<tr><td colspan="4" class="an-empty">집계할 매출이 없습니다.</td></tr>`;
  }
})();
